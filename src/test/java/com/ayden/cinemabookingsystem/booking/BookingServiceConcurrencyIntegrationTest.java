package com.ayden.cinemabookingsystem.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class BookingServiceConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DataSource dataSource;

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

            // Create two test users
            userA = insertId(
                    c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('User A', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com"
            );

            userB = insertId(
                    c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('User B', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com"
            );

            // Create test movie
            movieId = insertId(
                    c,
                    """
                    INSERT INTO movie (title, duration_minutes)
                    VALUES (?, 120)
                    RETURNING id
                    """,
                    "Concurrency Movie " + UUID.randomUUID()
            );

            // Create test cinema hall
            hallId = insertId(
                    c,
                    """
                    INSERT INTO cinema_hall (name)
                    VALUES (?)
                    RETURNING id
                    """,
                    "Concurrency Hall " + UUID.randomUUID()
            );

            // Create future showtime
            OffsetDateTime startsAt =
                    OffsetDateTime.now().plusDays(20);

            showtimeId = insertId(
                    c,
                    """
                    INSERT INTO showtime
                        (movie_id, hall_id, starts_at, ends_at)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    movieId,
                    hallId,
                    startsAt,
                    startsAt.plusHours(2)
            );

            // Create three seats: A1, A2, A3
            seatA1 = insertSeat(c, 1);
            seatA2 = insertSeat(c, 2);
            seatA3 = insertSeat(c, 3);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {

        try (Connection c = dataSource.getConnection()) {

            execute(
                    c,
                    "DELETE FROM booking_seat WHERE showtime_id = ?",
                    showtimeId
            );

            execute(
                    c,
                    "DELETE FROM booking WHERE showtime_id = ?",
                    showtimeId
            );

            execute(
                    c,
                    "DELETE FROM showtime WHERE id = ?",
                    showtimeId
            );

            execute(
                    c,
                    "DELETE FROM seat WHERE hall_id = ?",
                    hallId
            );

            execute(
                    c,
                    "DELETE FROM cinema_hall WHERE id = ?",
                    hallId
            );

            execute(
                    c,
                    "DELETE FROM movie WHERE id = ?",
                    movieId
            );

            execute(
                    c,
                    "DELETE FROM app_user WHERE id IN (?, ?)",
                    userA,
                    userB
            );
        }
    }

    // TEST 1: Two users compete for the same seat

    @Test
    void concurrentBookingServiceCalls_onlyOneSucceeds()
            throws Exception {

        List<String> results = runConcurrent(
                () -> attemptBooking(userA, seatA1),
                () -> attemptBooking(userB, seatA1)
        );

        assertResults(results);

        // Only one booking should exist
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking
                        WHERE showtime_id = ?
                        """,
                        showtimeId
                )
        );

        // A1 must only be reserved once
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE showtime_id = ?
                          AND seat_id = ?
                          AND status = 'RESERVED'
                        """,
                        showtimeId,
                        seatA1
                )
        );
    }

    // TEST 2: Two users compete for overlapping seats

    @Test
    void concurrentMultiSeatBookings_onlyOneCompleteOrderSucceeds()
            throws Exception {

        List<String> results = runConcurrent(
                () -> attemptBooking(userA, seatA1, seatA2),
                () -> attemptBooking(userB, seatA2, seatA3)
        );

        assertResults(results);

        // Losing transaction must not leave a booking
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking
                        WHERE showtime_id = ?
                        """,
                        showtimeId
                )
        );

        // Winning booking must contain exactly two seats
        assertEquals(
                2,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE showtime_id = ?
                          AND status = 'RESERVED'
                        """,
                        showtimeId
                )
        );

        // Shared seat A2 must only be reserved once
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE showtime_id = ?
                          AND seat_id = ?
                          AND status = 'RESERVED'
                        """,
                        showtimeId,
                        seatA2
                )
        );

        // The successful booking must contain exactly 2 seats
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM (
                            SELECT booking_id
                            FROM booking_seat
                            WHERE showtime_id = ?
                              AND status = 'RESERVED'
                            GROUP BY booking_id
                            HAVING COUNT(*) = 2
                        ) complete_booking
                        """,
                        showtimeId
                )
        );
    }

    // Run two calls at approximately the same time

    private List<String> runConcurrent(
            Callable<String> taskA,
            Callable<String> taskB
    ) throws Exception {

        ExecutorService pool =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {

            Future<String> first = pool.submit(() -> {
                ready.countDown();
                start.await();
                return taskA.call();
            });

            Future<String> second = pool.submit(() -> {
                ready.countDown();
                start.await();
                return taskB.call();
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Workers were not ready"
            );

            start.countDown();

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

        } finally {

            start.countDown();
            pool.shutdownNow();

            assertTrue(
                    pool.awaitTermination(5, TimeUnit.SECONDS),
                    "Workers did not terminate"
            );
        }
    }

    // Call the real Spring BookingService

    private String attemptBooking(
            long userId,
            Long... seatIds
    ) {

        // New request DTO does not contain userId
        CreateBookingRequest request =
                new CreateBookingRequest(
                        showtimeId,
                        List.of(seatIds)
                );

        try {

            // The first argument represents authenticated user ID
            bookingService.createBooking(userId, request);

            return "SUCCESS";

        } catch (DataIntegrityViolationException ex) {

            String state = findSqlState(ex);

            return state == null
                    ? "UNKNOWN_SQL_ERROR"
                    : state;
        }
    }

    // Verify exactly one success and one unique conflict

    private void assertResults(List<String> results) {

        assertEquals(
                1,
                results.stream()
                        .filter("SUCCESS"::equals)
                        .count(),
                "Expected exactly one successful booking: " + results
        );

        assertEquals(
                1,
                results.stream()
                        .filter("23505"::equals)
                        .count(),
                "Expected exactly one PostgreSQL unique conflict: " + results
        );
    }

    // Extract SQLSTATE from nested exceptions

    private String findSqlState(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {

            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }

            current = current.getCause();
        }

        return null;
    }

    // Insert a seat and return its generated ID

    private long insertSeat(
            Connection connection,
            int seatNumber
    ) throws SQLException {

        return insertId(
                connection,
                """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """,
                hallId,
                seatNumber
        );
    }

    // Execute INSERT ... RETURNING id

    private long insertId(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            bind(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(result.next());

                return result.getLong(1);
            }
        }
    }

    // Execute SQL statements

    private void execute(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            bind(statement, parameters);

            statement.executeUpdate();
        }
    }

    // Count rows

    private int count(
            String sql,
            Object... parameters
    ) throws SQLException {

        try (Connection connection =
                     dataSource.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            bind(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(result.next());

                return result.getInt(1);
            }
        }
    }

    // Bind PreparedStatement parameters

    private void bind(
            PreparedStatement statement,
            Object... parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
