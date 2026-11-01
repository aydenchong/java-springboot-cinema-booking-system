package com.ayden.cinemabookingsystem.admin;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCancellationIntegrationTest {

    private static final String AUDIT_INSERT_MARKER =
            "INSERT INTO admin_booking_audit";

    private static final String CANCELLATION_REASON =
            "Projector maintenance requires cancellation";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoSpyBean
    private JdbcTemplate jdbcTemplateSpy;

    private String fixtureKey;
    private Long adminId;
    private Long customerId;
    private Long movieId;
    private Long hallId;
    private Long showtimeId;
    private Long bookingId;
    private final List<Long> seatIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtureKey = UUID.randomUUID().toString();

        adminId = insertUser(
                "Admin Cancellation " + fixtureKey,
                "admin-cancellation-" + fixtureKey + "@example.test",
                "ADMIN"
        );

        customerId = insertUser(
                "Customer Cancellation " + fixtureKey,
                "customer-cancellation-" + fixtureKey + "@example.test",
                "CUSTOMER"
        );

        movieId = jdbcTemplate.queryForObject(
                """
                INSERT INTO movie (title, duration_minutes)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                "Admin cancellation movie " + fixtureKey,
                120
        );

        hallId = jdbcTemplate.queryForObject(
                """
                INSERT INTO cinema_hall (name, hall_type)
                VALUES (?, 'STANDARD')
                RETURNING id
                """,
                Long.class,
                "Admin cancellation hall " + fixtureKey
        );

        OffsetDateTime startsAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusDays(3)
                .withNano(0);

        showtimeId = jdbcTemplate.queryForObject(
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

        seatIds.add(insertSeat("A", 1));
        seatIds.add(insertSeat("A", 2));
        seatIds.add(insertSeat("A", 3));

        bookingId = jdbcTemplate.queryForObject(
                """
                INSERT INTO booking (user_id, showtime_id)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                customerId,
                showtimeId
        );

        for (Long seatId : seatIds) {
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

    @AfterEach
    void tearDown() {
        if (bookingId != null) {
            jdbcTemplate.update(
                    "DELETE FROM admin_booking_audit WHERE booking_id = ?",
                    bookingId
            );
            jdbcTemplate.update(
                    "DELETE FROM booking_seat WHERE booking_id = ?",
                    bookingId
            );
            jdbcTemplate.update(
                    "DELETE FROM booking WHERE id = ?",
                    bookingId
            );
        }

        if (showtimeId != null) {
            jdbcTemplate.update(
                    "DELETE FROM showtime WHERE id = ?",
                    showtimeId
            );
        }

        for (Long seatId : seatIds) {
            jdbcTemplate.update(
                    "DELETE FROM seat WHERE id = ?",
                    seatId
            );
        }

        if (hallId != null) {
            jdbcTemplate.update(
                    "DELETE FROM cinema_hall WHERE id = ?",
                    hallId
            );
        }

        if (movieId != null) {
            jdbcTemplate.update(
                    "DELETE FROM movie WHERE id = ?",
                    movieId
            );
        }

        if (customerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    customerId
            );
        }

        if (adminId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    adminId
            );
        }

        seatIds.clear();
    }

    @Test
    void adminCancelsAnotherCustomersConfirmedFutureBooking_andWritesAudit()
            throws Exception {

        mockMvc.perform(adminCancelRequest(bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.userId").value(customerId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seats.length()").value(3));

        Map<String, Object> booking = jdbcTemplate.queryForMap(
                """
                SELECT status, cancelled_at
                FROM booking
                WHERE id = ?
                """,
                bookingId
        );

        assertEquals("CANCELLED", booking.get("status"));
        assertNotNull(booking.get("cancelled_at"));

        assertEquals(
                3L,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE booking_id = ?
                          AND status = 'CANCELLED'
                        """,
                        bookingId
                )
        );

        assertEquals(
                0L,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE booking_id = ?
                          AND status = 'RESERVED'
                        """,
                        bookingId
                )
        );

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                SELECT admin_user_id, booking_id, action, reason, created_at
                FROM admin_booking_audit
                WHERE booking_id = ?
                """,
                bookingId
        );

        assertEquals(adminId, ((Number) audit.get("admin_user_id")).longValue());
        assertEquals(bookingId, ((Number) audit.get("booking_id")).longValue());
        assertEquals("CANCEL", audit.get("action"));
        assertEquals(CANCELLATION_REASON, audit.get("reason"));
        assertNotNull(audit.get("created_at"));
        assertEquals(
                1L,
                count(
                        "SELECT COUNT(*) FROM admin_booking_audit " +
                                "WHERE booking_id = ?",
                        bookingId
                )
        );
    }

    @Test
    void repeatedCancellation_returns409_andDoesNotWriteSecondAudit()
            throws Exception {

        mockMvc.perform(adminCancelRequest(bookingId))
                .andExpect(status().isOk());

        mockMvc.perform(adminCancelRequest(bookingId))
                .andExpect(status().isConflict());

        assertEquals(
                1L,
                count(
                        "SELECT COUNT(*) FROM admin_booking_audit " +
                                "WHERE booking_id = ?",
                        bookingId
                )
        );
        assertEquals(
                3L,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE booking_id = ?
                          AND status = 'CANCELLED'
                        """,
                        bookingId
                )
        );
    }

    @Test
    void nonexistentBooking_returns404() throws Exception {
        mockMvc.perform(adminCancelRequest(Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditInsertFailure_rollsBackBookingAndBookingSeats()
            throws Exception {

        doThrow(new DataAccessResourceFailureException(
                "Forced audit insertion failure"
        )).when(jdbcTemplateSpy).update(
                argThat(sql -> sql.contains(AUDIT_INSERT_MARKER)),
                any(Object[].class)
        );

        assertThrows(
                ServletException.class,
                () -> mockMvc.perform(adminCancelRequest(bookingId)).andReturn()
        );

        Map<String, Object> booking = jdbcTemplate.queryForMap(
                """
                SELECT status, cancelled_at
                FROM booking
                WHERE id = ?
                """,
                bookingId
        );

        assertEquals("CONFIRMED", booking.get("status"));
        assertNull(booking.get("cancelled_at"));

        assertEquals(
                3L,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE booking_id = ?
                          AND status = 'RESERVED'
                        """,
                        bookingId
                )
        );
        assertEquals(
                0L,
                count(
                        """
                        SELECT COUNT(*)
                        FROM booking_seat
                        WHERE booking_id = ?
                          AND status = 'CANCELLED'
                        """,
                        bookingId
                )
        );
        assertEquals(
                0L,
                count(
                        "SELECT COUNT(*) FROM admin_booking_audit " +
                                "WHERE booking_id = ?",
                        bookingId
                )
        );
    }

    private MockHttpServletRequestBuilder adminCancelRequest(Long targetBookingId) {
        return patch(
                "/api/v1/admin/bookings/{id}/cancel",
                targetBookingId
        )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + createToken(adminId, "ADMIN")
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reason": "%s"
                        }
                        """.formatted(CANCELLATION_REASON));
    }

    private String createToken(Long userId, String role) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cinereserve")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .claim("role", role)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    private Long insertUser(
            String displayName,
            String email,
            String role
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO app_user (display_name, email, role)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                displayName,
                email,
                role
        );
    }

    private Long insertSeat(String rowLabel, int seatNumber) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                hallId,
                rowLabel,
                seatNumber
        );
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                arguments
        );

        return result == null ? 0L : result;
    }
}
