package com.ayden.cinemabookingsystem.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class BookingHttpConcurrencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private long userA;
    private long userB;
    private long movieId;
    private long hallId;
    private long showtimeId;
    private long seatA1;
    private long seatA2;
    private long seatA3;

    @BeforeEach
    void setUp() throws SQLException {

        try (Connection c = dataSource.getConnection()) {

            userA = insertId(c, """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('HTTP User A', ?)
                    RETURNING id
                    """, UUID.randomUUID() + "@example.com");

            userB = insertId(c, """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('HTTP User B', ?)
                    RETURNING id
                    """, UUID.randomUUID() + "@example.com");

            movieId = insertId(c, """
                    INSERT INTO movie (title, duration_minutes)
                    VALUES (?, 120)
                    RETURNING id
                    """, "HTTP Movie " + UUID.randomUUID());

            hallId = insertId(c, """
                    INSERT INTO cinema_hall (name)
                    VALUES (?)
                    RETURNING id
                    """, "HTTP Hall " + UUID.randomUUID());

            OffsetDateTime start =
                    OffsetDateTime.now().plusDays(20);

            showtimeId = insertId(c, """
                    INSERT INTO showtime
                        (movie_id, hall_id, starts_at, ends_at)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    movieId,
                    hallId,
                    start,
                    start.plusHours(2)
            );

            seatA1 = insertSeat(c, 1);
            seatA2 = insertSeat(c, 2);
            seatA3 = insertSeat(c, 3);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {

        try (Connection c = dataSource.getConnection()) {

            execute(c,
                    "DELETE FROM booking_seat WHERE showtime_id = ?",
                    showtimeId);

            execute(c,
                    "DELETE FROM booking WHERE showtime_id = ?",
                    showtimeId);

            execute(c,
                    "DELETE FROM showtime WHERE id = ?",
                    showtimeId);

            execute(c,
                    "DELETE FROM seat WHERE hall_id = ?",
                    hallId);

            execute(c,
                    "DELETE FROM cinema_hall WHERE id = ?",
                    hallId);

            execute(c,
                    "DELETE FROM movie WHERE id = ?",
                    movieId);

            execute(c,
                    "DELETE FROM app_user WHERE id IN (?, ?)",
                    userA, userB);
        }
    }

    @Test
    void concurrentHttpRequests_onlyOneBookingSucceeds()
            throws Exception {

        String tokenA = createToken(userA);
        String tokenB = createToken(userB);

        List<Integer> statuses = runConcurrent(
                () -> postBooking(tokenA, seatA1),
                () -> postBooking(tokenB, seatA1)
        );

        assertHttpResults(statuses);

        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM booking
                WHERE showtime_id = ?
                """, showtimeId));

        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """, showtimeId, seatA1));
    }

    @Test
    void concurrentOverlappingHttpBookings_rollBackLoser()
            throws Exception {

        String tokenA = createToken(userA);
        String tokenB = createToken(userB);

        List<Integer> statuses = runConcurrent(
                () -> postBooking(tokenA, seatA1, seatA2),
                () -> postBooking(tokenB, seatA2, seatA3)
        );

        assertHttpResults(statuses);

        // Exactly one complete booking survives.
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM booking
                WHERE showtime_id = ?
                """, showtimeId));

        // The losing transaction must not reserve its other seat.
        assertEquals(2, count("""
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND status = 'RESERVED'
                """, showtimeId));

        // The overlapping seat cannot be double-booked.
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """, showtimeId, seatA2));
    }

    private String createToken(long userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .subject(Long.toString(userId))
                .claim("role", "CUSTOMER")
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    private int postBooking(
            String token,
            long... seatIds
    ) throws Exception {

        StringBuilder seatArray = new StringBuilder();

        for (int i = 0; i < seatIds.length; i++) {
            if (i > 0) {
                seatArray.append(",");
            }
            seatArray.append(seatIds[i]);
        }

        String json = """
                {
                  "showtimeId": %d,
                  "seatIds": [%s]
                }
                """.formatted(showtimeId, seatArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port +
                                "/api/v1/bookings"
                ))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return response.statusCode();
    }

    private List<Integer> runConcurrent(
            Callable<Integer> taskA,
            Callable<Integer> taskB
    ) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = pool.submit(() -> {
                ready.countDown();
                start.await();
                return taskA.call();
            });

            Future<Integer> second = pool.submit(() -> {
                ready.countDown();
                start.await();
                return taskB.call();
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(
                    pool.awaitTermination(5, TimeUnit.SECONDS)
            );
        }
    }

    private void assertHttpResults(List<Integer> statuses) {

        assertEquals(
                1,
                statuses.stream()
                        .filter(s -> s == HttpStatus.CREATED.value())
                        .count(),
                "Expected one HTTP 201, got: " + statuses
        );

        assertEquals(
                1,
                statuses.stream()
                        .filter(s -> s == HttpStatus.CONFLICT.value())
                        .count(),
                "Expected one HTTP 409, got: " + statuses
        );
    }

    private long insertSeat(
            Connection c,
            int number
    ) throws SQLException {

        return insertId(c, """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """, hallId, number);
    }

    private long insertId(
            Connection c,
            String sql,
            Object... params
    ) throws SQLException {

        try (PreparedStatement stmt =
                     c.prepareStatement(sql)) {

            bind(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void execute(
            Connection c,
            String sql,
            Object... params
    ) throws SQLException {

        try (PreparedStatement stmt =
                     c.prepareStatement(sql)) {

            bind(stmt, params);
            stmt.executeUpdate();
        }
    }

    private int count(
            String sql,
            Object... params
    ) throws SQLException {

        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {

            bind(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void bind(
            PreparedStatement stmt,
            Object... params
    ) throws SQLException {

        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}
