package com.ayden.cinemabookingsystem.hall;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void sameHall_cannotHaveDuplicateSeat() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long hallId = insertHall(connection);

                insertSeat(connection, hallId, "A", 1);

                SQLException exception = assertThrows(
                        SQLException.class,
                        () -> insertSeat(connection, hallId, "A", 1)
                );

                assertEquals("23505", exception.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void differentHalls_canHaveSameSeatCode() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long hall1 = insertHall(connection);
                long hall2 = insertHall(connection);

                insertSeat(connection, hall1, "A", 1);
                insertSeat(connection, hall2, "A", 1);

                assertEquals(1, countSeat(connection, hall1, "A", 1));
                assertEquals(1, countSeat(connection, hall2, "A", 1));

            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void seat_cannotReferenceNonexistentHall() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                SQLException exception = assertThrows(
                        SQLException.class,
                        () -> insertSeat(connection, -1L, "A", 1)
                );

                assertEquals("23503", exception.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }

    private long insertHall(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO cinema_hall (name, hall_type)
                VALUES (?, 'STANDARD')
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    "Integration Test Hall " + UUID.randomUUID()
            );

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong("id");
            }
        }
    }

    private void insertSeat(
            Connection connection,
            long hallId,
            String rowLabel,
            int seatNumber
    ) throws SQLException {

        String sql = """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, hallId);
            statement.setString(2, rowLabel);
            statement.setInt(3, seatNumber);

            statement.executeUpdate();
        }
    }

    private int countSeat(
            Connection connection,
            long hallId,
            String rowLabel,
            int seatNumber
    ) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM seat
                WHERE hall_id = ?
                  AND row_label = ?
                  AND seat_number = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, hallId);
            statement.setString(2, rowLabel);
            statement.setInt(3, seatNumber);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }
}
