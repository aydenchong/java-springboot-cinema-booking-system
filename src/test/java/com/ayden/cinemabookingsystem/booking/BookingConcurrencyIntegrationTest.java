package com.ayden.cinemabookingsystem.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private long movieId;
    private long hallId;
    private long showtimeId;
    private long userA;
    private long userB;
    private long seatA1;
    private long seatA2;
    private long seatA3;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = dataSource.getConnection()) {

            movieId = insertReturningId(c,
                    "INSERT INTO movie (title, duration_minutes) " +
                            "VALUES (?, 120) RETURNING id",
                    "Concurrent Test " + UUID.randomUUID());

            hallId = insertReturningId(c,
                    "INSERT INTO cinema_hall (name) " +
                            "VALUES (?) RETURNING id",
                    "Concurrent Hall " + UUID.randomUUID());

            OffsetDateTime start =
                    OffsetDateTime.now().plusDays(20);

            showtimeId = insertReturningId(c,
                    """
                    INSERT INTO showtime
                    (movie_id, hall_id, starts_at, ends_at)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    movieId, hallId, start, start.plusHours(2));

            userA = insertUser(c, "User A");
            userB = insertUser(c, "User B");

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
                    "DELETE FROM app_user WHERE id IN (?, ?)",
                    userA, userB);

            execute(c,
                    "DELETE FROM movie WHERE id = ?",
                    movieId);
        }
    }

    @Test
    void twoUsersBookingSameSeat_onlyOneSucceeds()
            throws Exception {

        List<String> results = runConcurrent(
                () -> attemptBooking(userA, seatA1),
                () -> attemptBooking(userB, seatA1)
        );

        assertEquals(1, Collections.frequency(results, "SUCCESS"));
        assertEquals(1, Collections.frequency(results, "23505"));

        assertEquals(1, count(
                "SELECT COUNT(*) FROM booking WHERE showtime_id = ?",
                showtimeId));

        assertEquals(1, count(
                """
                SELECT COUNT(*) FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId, seatA1));
    }

    @Test
    void overlappingMultiSeatBookings_mustRollbackLosingOrder()
            throws Exception {

        List<String> results = runConcurrent(
                () -> attemptBooking(userA, seatA1, seatA2),
                () -> attemptBooking(userB, seatA2, seatA3)
        );

        assertEquals(1, Collections.frequency(results, "SUCCESS"));
        assertEquals(1, Collections.frequency(results, "23505"));

        // Only the winning booking should remain.
        assertEquals(1, count(
                "SELECT COUNT(*) FROM booking WHERE showtime_id = ?",
                showtimeId));

        // A complete booking contains exactly two seats.
        assertEquals(2, count(
                """
                SELECT COUNT(*) FROM booking_seat
                WHERE showtime_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId));

        // Shared seat A2 must only be reserved once.
        assertEquals(1, count(
                """
                SELECT COUNT(*) FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId, seatA2));
    }

    @Test
    void cancelledSeat_canBeBookedAgain()
            throws SQLException {

        assertEquals("SUCCESS",
                attemptBooking(userA, seatA1));

        long bookingId;

        try (Connection c = dataSource.getConnection()) {
            bookingId = findBookingForUser(c, userA);

            c.setAutoCommit(false);

            try {
                OffsetDateTime cancelledAt = OffsetDateTime.now();

                execute(c,
                        """
                        UPDATE booking
                        SET status = 'CANCELLED',
                            cancelled_at = ?
                        WHERE id = ?
                        """,
                        cancelledAt, bookingId);

                execute(c,
                        """
                        UPDATE booking_seat
                        SET status = 'CANCELLED',
                            cancelled_at = ?
                        WHERE booking_id = ?
                        """,
                        cancelledAt, bookingId);

                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }

        assertEquals("SUCCESS",
                attemptBooking(userB, seatA1));

        // Keep the cancelled history and new reservation.
        assertEquals(2, count(
                "SELECT COUNT(*) FROM booking WHERE showtime_id = ?",
                showtimeId));

        assertEquals(1, count(
                """
                SELECT COUNT(*) FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'CANCELLED'
                """,
                showtimeId, seatA1));

        assertEquals(1, count(
                """
                SELECT COUNT(*) FROM booking_seat
                WHERE showtime_id = ?
                  AND seat_id = ?
                  AND status = 'RESERVED'
                """,
                showtimeId, seatA1));
    }

    private List<String> runConcurrent(
            Callable<String> taskA,
            Callable<String> taskB
    ) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<String> wrappedA = () -> {
                ready.countDown();
                start.await();
                return taskA.call();
            };

            Callable<String> wrappedB = () -> {
                ready.countDown();
                start.await();
                return taskB.call();
            };

            Future<String> futureA = pool.submit(wrappedA);
            Future<String> futureB = pool.submit(wrappedB);

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "Workers did not become ready");

            start.countDown();

            return List.of(
                    futureA.get(20, TimeUnit.SECONDS),
                    futureB.get(20, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS),
                    "Workers did not terminate");
        }
    }

    private String attemptBooking(
            long userId,
            long... seatIds
    ) {
        try (Connection c = dataSource.getConnection()) {

            c.setAutoCommit(false);

            try {
                long bookingId = insertReturningId(c,
                        """
                        INSERT INTO booking (user_id, showtime_id)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                        userId, showtimeId);

                for (long seatId : seatIds) {
                    execute(c,
                            """
                            INSERT INTO booking_seat
                            (booking_id, showtime_id, hall_id, seat_id)
                            VALUES (?, ?, ?, ?)
                            """,
                            bookingId, showtimeId, hallId, seatId);
                }

                c.commit();
                return "SUCCESS";

            } catch (SQLException ex) {
                c.rollback();
                return ex.getSQLState();
            }
        } catch (SQLException ex) {
            return ex.getSQLState();
        }
    }

    private long insertUser(Connection c, String name)
            throws SQLException {
        return insertReturningId(c,
                """
                INSERT INTO app_user (display_name, email)
                VALUES (?, ?)
                RETURNING id
                """,
                name,
                UUID.randomUUID() + "@example.com");
    }

    private long insertSeat(Connection c, int number)
            throws SQLException {
        return insertReturningId(c,
                """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """,
                hallId, number);
    }

    private long findBookingForUser(Connection c, long userId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM booking WHERE user_id = ? AND showtime_id = ?"
        )) {
            ps.setLong(1, userId);
            ps.setLong(2, showtimeId);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private long insertReturningId(
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

