package com.ayden.cinemabookingsystem.booking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class BookingNPlusOneIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional
    void lazyLoadingUsers_causesNPlusOneQueries() {

        // 1. Create three different users
        long userA = createUser("User A");
        long userB = createUser("User B");
        long userC = createUser("User C");

        // 2. Create a movie
        Long movieId = jdbcTemplate.queryForObject(
                """
                INSERT INTO movie (title, duration_minutes)
                VALUES (?, 120)
                RETURNING id
                """,
                Long.class,
                "N+1 Movie " + UUID.randomUUID()
        );

        // 3. Create a cinema hall
        Long hallId = jdbcTemplate.queryForObject(
                """
                INSERT INTO cinema_hall (name)
                VALUES (?)
                RETURNING id
                """,
                Long.class,
                "N+1 Hall " + UUID.randomUUID()
        );

        // 4. Create a future showtime
        OffsetDateTime startsAt =
                OffsetDateTime.now().plusDays(20);

        Long showtimeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO showtime
                    (movie_id, hall_id, starts_at, ends_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                movieId,
                hallId,
                startsAt,
                startsAt.plusHours(2)
        );

        // 5. Create three bookings and their seats
        createBookingWithSeat(userA, showtimeId, hallId, 1);
        createBookingWithSeat(userB, showtimeId, hallId, 2);
        createBookingWithSeat(userC, showtimeId, hallId, 3);

        // 6. Clear the persistence context
        entityManager.clear();

        // 7. Enable Hibernate Statistics
        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        Statistics statistics =
                sessionFactory.getStatistics();

        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 8. Load three bookings
        List<Booking> bookings =
                bookingRepository.findByShowtime_Id(showtimeId);

        assertEquals(3, bookings.size());

        // 9. Access the LAZY user relationships
        for (Booking booking : bookings) {
            String name =
                    booking.getUser().getDisplayName();

            System.out.println("Booking User: " + name);

            assertNotNull(name);
        }

        // 10. Record original N+1 query count
        long beforeCount = statistics.getPrepareStatementCount();

        assertEquals(4, beforeCount);

        // 11. Clear previously loaded entities
                entityManager.clear();

        // 12. Reset statistics
                statistics.clear();

        // 13. Execute optimized query
                List<Booking> optimizedBookings =
                        bookingRepository.findByShowtimeWithUser(showtimeId);

                assertEquals(3, optimizedBookings.size());

        // 14. Access user details again
                for (Booking booking : optimizedBookings) {
                    String name = booking.getUser().getDisplayName();

                    System.out.println("Optimized Booking User: " + name);

                    assertNotNull(name);
                }

        // 15. Count SQL statements after optimization
                long afterCount = statistics.getPrepareStatementCount();

                System.out.println("Before JOIN FETCH: " + beforeCount);
                System.out.println("After JOIN FETCH: " + afterCount);

        // 16. Verify optimization
                assertEquals(1, afterCount);
    }

    private long createUser(String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO app_user (display_name, email)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                UUID.randomUUID() + "@example.com"
        );
    }

    private void createBookingWithSeat(
            long userId,
            long showtimeId,
            long hallId,
            int seatNumber
    ) {

        Long seatId = jdbcTemplate.queryForObject(
                """
                INSERT INTO seat
                    (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """,
                Long.class,
                hallId,
                seatNumber
        );

        Long bookingId = jdbcTemplate.queryForObject(
                """
                INSERT INTO booking (user_id, showtime_id)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                userId,
                showtimeId
        );

        jdbcTemplate.update(
                """
                INSERT INTO booking_seat
                    (booking_id, showtime_id, hall_id, seat_id)
                VALUES (?, ?, ?, ?)
                """,
                bookingId,
                showtimeId,
                hallId,
                seatId
        );
    }
}
