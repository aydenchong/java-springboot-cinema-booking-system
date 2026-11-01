package com.ayden.cinemabookingsystem.showtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ShowtimeDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private Long movieId;
    private Long hallId;

    private OffsetDateTime startTime;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            movieId = insertMovie(connection);
            hallId = insertHall(connection);
        }

        startTime = OffsetDateTime.now()
                .plusDays(20)
                .truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (hallId != null) {
                executeDelete(
                        connection,
                        "DELETE FROM showtime WHERE hall_id = ?",
                        hallId
                );
                executeDelete(
                        connection,
                        "DELETE FROM cinema_hall WHERE id = ?",
                        hallId
                );
            }

            if (movieId != null) {
                executeDelete(
                        connection,
                        "DELETE FROM movie WHERE id = ?",
                        movieId
                );
            }
        }
    }

    @Test
    void overlappingShowtimes_shouldBeRejected() throws SQLException {

        insertShowtime(
                startTime,
                startTime.plusHours(2)
        );

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertShowtime(
                        startTime.plusHours(1),
                        startTime.plusHours(3)
                )
        );

        assertEquals("23P01", exception.getSQLState());
        assertEquals(1, countShowtimes());
    }

    @Test
    void adjacentShowtimes_shouldBeAllowed() throws SQLException {

        insertShowtime(
                startTime,
                startTime.plusHours(2)
        );

        insertShowtime(
                startTime.plusHours(2),
                startTime.plusHours(4)
        );

        assertEquals(2, countShowtimes());
    }

    @Test
    void concurrentOverlappingShowtimes_onlyOneShouldSucceed()
            throws Exception {

        CountDownLatch startLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> {
                startLatch.await();
                return attemptInsert(
                        startTime,
                        startTime.plusHours(2)
                );
            });

            Future<String> second = executor.submit(() -> {
                startLatch.await();
                return attemptInsert(
                        startTime.plusHours(1),
                        startTime.plusHours(3)
                );
            });

            // Allow both workers to start together
            startLatch.countDown();

            String result1 = first.get(15, TimeUnit.SECONDS);
            String result2 = second.get(15, TimeUnit.SECONDS);

            int successCount = 0;
            int conflictCount = 0;

            for (String result : new String[]{result1, result2}) {
                if ("SUCCESS".equals(result)) {
                    successCount++;
                } else if ("23P01".equals(result)) {
                    conflictCount++;
                } else {
                    fail("Unexpected database result: " + result);
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, conflictCount);
            assertEquals(1, countShowtimes());

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(
                    5, TimeUnit.SECONDS
            ), "Worker threads did not terminate");
        }
    }

    private String attemptInsert(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        try {
            insertShowtime(startsAt, endsAt);
            return "SUCCESS";
        } catch (SQLException ex) {
            return ex.getSQLState();
        }
    }

    private long insertMovie(Connection connection)
            throws SQLException {

        String sql = """
                INSERT INTO movie (
                    title,
                    duration_minutes,
                    status
                )
                VALUES (?, 120, 'ACTIVE')
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    "Test Movie " + UUID.randomUUID()
            );

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private long insertHall(Connection connection)
            throws SQLException {

        String sql = """
                INSERT INTO cinema_hall (
                    name,
                    hall_type
                )
                VALUES (?, 'STANDARD')
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    "Test Hall " + UUID.randomUUID()
            );

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void insertShowtime(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) throws SQLException {

        String sql = """
                INSERT INTO showtime (
                    movie_id,
                    hall_id,
                    starts_at,
                    ends_at,
                    status
                )
                VALUES (?, ?, ?, ?, 'SCHEDULED')
                """;

        // Each connection uses its own committed transaction.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, movieId);
            statement.setLong(2, hallId);
            statement.setObject(3, startsAt);
            statement.setObject(4, endsAt);

            statement.executeUpdate();
        }
    }

    private int countShowtimes() throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM showtime
                WHERE hall_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, hallId);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void executeDelete(
            Connection connection,
            String sql,
            long id
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
