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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
    private long seatId;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = dataSource.getConnection()) {

            userA = insertId(c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('User A', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com");

            userB = insertId(c,
                    """
                    INSERT INTO app_user (display_name, email)
                    VALUES ('User B', ?)
                    RETURNING id
                    """,
                    UUID.randomUUID() + "@example.com");

            movieId = insertId(c,
                    """
                    INSERT INTO movie (title, duration_minutes)
                    VALUES (?, 120)
                    RETURNING id
                    """,
                    "Concurrency Movie " + UUID.randomUUID());

            hallId = insertId(c,
                    """
                    INSERT INTO cinema_hall (name)
                    VALUES (?)
                    RETURNING id
                    """,
                    "Concurrency Hall " + UUID.randomUUID());

            OffsetDateTime startsAt =
                    OffsetDateTime.now().plusDays(20);

            showtimeId = insertId(c,
                    """
                    INSERT INTO showtime
                        (movie_id, hall_id, starts_at, ends_at)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    movieId, hallId,
                    startsAt, startsAt.plusHours(2));

            seatId = insertId(c,
                    """
                    INSERT INTO seat
                        (hall_id, row_label, seat_number)
                    VALUES (?, 'A', 1)
                    RETURNING id
                    """,
                    hallId);
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
                    "DELETE FROM seat WHERE hall_id = ?",
                    hallId);

            execute(c,
                    "DELETE FROM showtime WHERE id = ?",
                    showtimeId);

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
    void concurrentBookingServiceCalls_onlyOneSucceeds()
            throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = pool.submit(
                    () -> attemptBooking(userA, ready, start)
            );

            Future<String> second = pool.submit(
                    () -> attemptBooking(userB, ready, start)
            );

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            String resultA = first.get(20, TimeUnit.SECONDS);
            String resultB = second.get(20, TimeUnit.SECONDS);

            List<String> results = List.of(resultA, resultB);

            assertEquals(1,
                    results.stream()
                            .filter("SUCCESS"::equals)
                            .count());

            assertEquals(1,
                    results.stream()
                            .filter("23505"::equals)
                            .count());

            assertEquals(1, count(
                    """
                    SELECT COUNT(*) FROM booking
                    WHERE showtime_id = ?
                    """,
                    showtimeId));

            assertEquals(1, count(
                    """
                    SELECT COUNT(*) FROM booking_seat
                    WHERE showtime_id = ?
                      AND seat_id = ?
                      AND status = 'RESERVED'
                    """,
                    showtimeId, seatId));

        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(
                    5, TimeUnit.SECONDS
            ));
        }
    }

    private String attemptBooking(
            long userId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {

        ready.countDown();
        start.await();

        CreateBookingRequest request =
                new CreateBookingRequest(
                        userId,
                        showtimeId,
                        List.of(seatId)
                );

        try {
            bookingService.createBooking(request);
            return "SUCCESS";

        } catch (DataIntegrityViolationException ex) {
            String state = findSqlState(ex);
            return state == null ? "UNKNOWN_SQL_ERROR" : state;
        }
    }

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

    private int count(
            String sql,
            Object... parameters
    ) throws SQLException {

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            bind(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void bind(
            PreparedStatement statement,
            Object... parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}

