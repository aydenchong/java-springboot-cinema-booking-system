package com.ayden.cinemabookingsystem.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
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

    @Autowired
    private DataSource dataSource;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
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

            userA = insertId(c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('HTTP User A', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com");

            userB = insertId(c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('HTTP User B', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com");

            movieId = insertId(c,
                    """
                    INSERT INTO movie (title, duration_minutes)
                    VALUES (?, 120)
                    RETURNING id
                    """,
                    "HTTP Test Movie " + UUID.randomUUID());

            hallId = insertId(c,
                    """
                    INSERT INTO cinema_hall (name)
                    VALUES (?)
                    RETURNING id
                    """,
                    "HTTP Test Hall " + UUID.randomUUID());

            OffsetDateTime startsAt =
                    OffsetDateTime.now().plusDays(20);

            showtimeId = insertId(c,
                    """
                    INSERT INTO showtime
                        (movie_id, hall_id, starts_at, ends_at)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    movieId,
                    hallId,
                    startsAt,
                    startsAt.plusHours(2));

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
    void concurrentHttpRequests_sameSeat_returns201And409()
            throws Exception {

        List<Integer> results = runConcurrent(
                () -> postBooking(userA, seatA1),
                () -> postBooking(userB, seatA1)
        );

        assertHttpResults(results);

        assertDatabaseState(1, 1);

        assertEquals(1, count(
                """
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId, seatA1
        ));
    }

    @Test
    void concurrentHttpRequests_overlappingSeats_areAtomic()
            throws Exception {

        List<Integer> results = runConcurrent(
                () -> postBooking(userA, seatA1, seatA2),
                () -> postBooking(userB, seatA2, seatA3)
        );

        assertHttpResults(results);

        // Only one complete order should remain.
        assertDatabaseState(1, 2);

        assertEquals(1, count(
                """
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId, seatA2
        ));
    }

    private void assertHttpResults(List<Integer> results) {
        assertEquals(
                1,
                results.stream()
                        .filter(code -> code == 201)
                        .count(),
                "Exactly one request should return 201: " + results
        );

        assertEquals(
                1,
                results.stream()
                        .filter(code -> code == 409)
                        .count(),
                "Exactly one request should return 409: " + results
        );
    }

    private void assertDatabaseState(
            int expectedBookings,
            int expectedSeats
    ) throws SQLException {

        assertEquals(expectedBookings, count(
                """
                SELECT COUNT(*)
                FROM booking
                WHERE showtime_id = ?
                """,
                showtimeId
        ));

        assertEquals(expectedSeats, count(
                """
                SELECT COUNT(*)
                FROM booking_seat
                WHERE showtime_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId
        ));
    }

    private int postBooking(long userId, long... seatIds)
            throws Exception {

        StringBuilder ids = new StringBuilder();

        for (int i = 0; i < seatIds.length; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append(seatIds[i]);
        }

        String json = """
                {
                  "userId": %d,
                  "showtimeId": %d,
                  "seatIds": [%s]
                }
                """.formatted(userId, showtimeId, ids);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/api/v1/bookings"
                ))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println(
                "HTTP " + response.statusCode()
                        + ": " + response.body()
        );

        return response.statusCode();
    }

    private List<Integer> runConcurrent(
            Callable<Integer> taskA,
            Callable<Integer> taskB
    ) throws Exception {

        ExecutorService pool =
                Executors.newFixedThreadPool(2);

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

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "HTTP workers were not ready"
            );

            start.countDown();

            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(
                    pool.awaitTermination(5, TimeUnit.SECONDS),
                    "HTTP workers did not terminate"
            );
        }
    }

    private long insertSeat(Connection c, int number)
            throws SQLException {

        return insertId(c,
                """
                INSERT INTO seat
                    (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """,
                hallId, number);
    }

    private long insertId(
            Connection c,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, parameters);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void execute(
            Connection c,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, parameters);
            ps.executeUpdate();
        }
    }

    private int count(
            String sql,
            Object... parameters
    ) throws SQLException {

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            bind(ps, parameters);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void bind(
            PreparedStatement ps,
            Object... parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.length; i++) {
            ps.setObject(i + 1, parameters[i]);
        }
    }
}
