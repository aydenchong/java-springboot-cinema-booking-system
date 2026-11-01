package com.ayden.cinemabookingsystem.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    private String fixtureKey;
    private String sharedCustomerName;
    private String firstEmail;

    private Long adminId;
    private Long firstCustomerId;
    private Long secondCustomerId;
    private Long movieId;
    private Long hallId;
    private Long showtimeId;
    private Long firstBookingId;
    private Long secondBookingId;

    private final List<Long> seatIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtureKey = UUID.randomUUID().toString();
        sharedCustomerName = "Query Customer " + fixtureKey;
        firstEmail = "query-first-" + fixtureKey + "@example.test";

        adminId = insertUser(
                "Query Admin " + fixtureKey,
                "query-admin-" + fixtureKey + "@example.test",
                "ADMIN"
        );

        firstCustomerId = insertUser(
                sharedCustomerName + " One",
                firstEmail,
                "CUSTOMER"
        );

        secondCustomerId = insertUser(
                sharedCustomerName + " Two",
                "query-second-" + fixtureKey + "@example.test",
                "CUSTOMER"
        );

        movieId = jdbcTemplate.queryForObject(
                """
                INSERT INTO movie (title, duration_minutes)
                VALUES (?, 120)
                RETURNING id
                """,
                Long.class,
                "Query Test Movie " + fixtureKey
        );

        hallId = jdbcTemplate.queryForObject(
                """
                INSERT INTO cinema_hall (name, hall_type)
                VALUES (?, 'STANDARD')
                RETURNING id
                """,
                Long.class,
                "Query Test Hall " + fixtureKey
        );

        OffsetDateTime startsAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusDays(10)
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

        Long firstSeatId = insertSeat(1);
        Long secondSeatId = insertSeat(2);

        seatIds.add(firstSeatId);
        seatIds.add(secondSeatId);

        firstBookingId = insertBooking(firstCustomerId, firstSeatId);
        secondBookingId = insertBooking(secondCustomerId, secondSeatId);
    }

    @Test
    void adminCanSearchBookingsByUserId() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings")
                                .param("userId", firstCustomerId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(firstBookingId));
    }

    @Test
    void adminCanSearchBookingsByEmail() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings")
                                .param("email", firstEmail)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(firstBookingId));    }

    @Test
    void adminCanSearchBookingsByDisplayName() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings")
                                .param("displayName", sharedCustomerName)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void adminBookingSearchSupportsPagination() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings")
                                .param("displayName", sharedCustomerName)
                                .param("page", "0")
                                .param("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings")
                                .param("displayName", sharedCustomerName)
                                .param("page", "1")
                                .param("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void adminCanReadAnotherCustomersBookingDetail() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings/" + firstBookingId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstBookingId))
                .andExpect(jsonPath("$.userId").value(firstCustomerId))
                .andExpect(jsonPath("$.seats.length()").value(1));
    }

    @Test
    void nonexistentBookingDetailReturns404() throws Exception {
        mockMvc.perform(
                        adminGet("/api/v1/admin/bookings/" + Long.MAX_VALUE)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanSearchAuditLogsByBookingIdAndAdminId()
            throws Exception {

        Long auditId = createAuditRecordThroughCancellation();

        mockMvc.perform(
                        adminGet("/api/v1/admin/booking-audits")
                                .param("bookingId", firstBookingId.toString())
                                .param("adminId", adminId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(auditId))
                .andExpect(jsonPath("$.content[0].bookingId")
                        .value(firstBookingId))
                .andExpect(jsonPath("$.content[0].adminUserId")
                        .value(adminId));
    }

    @Test
    void adminCanReadAuditDetail() throws Exception {
        Long auditId = createAuditRecordThroughCancellation();

        mockMvc.perform(
                        adminGet("/api/v1/admin/booking-audits/" + auditId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auditId))
                .andExpect(jsonPath("$.bookingId").value(firstBookingId))
                .andExpect(jsonPath("$.adminUserId").value(adminId))
                .andExpect(jsonPath("$.reason")
                        .value("Automated audit query test"));
    }

    @Test
    void nonexistentAuditDetailReturns404() throws Exception {
        mockMvc.perform(
                adminGet(
                        "/api/v1/admin/booking-audits/" + Long.MAX_VALUE
                )
        ).andExpect(status().isNotFound());
    }

    private Long createAuditRecordThroughCancellation()
            throws Exception {

        mockMvc.perform(
                patch(
                        "/api/v1/admin/bookings/{id}/cancel",
                        firstBookingId
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + createAdminToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Automated audit query test"
                                }
                                """)
        ).andExpect(status().isOk());

        Long auditId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM admin_booking_audit
                WHERE booking_id = ?
                  AND admin_user_id = ?
                """,
                Long.class,
                firstBookingId,
                adminId
        );

        return assertDoesNotThrow(
                () -> java.util.Objects.requireNonNull(auditId)
        );
    }

    private org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder adminGet(String path) {

        return get(path).header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + createAdminToken()
        );
    }

    private String createAdminToken() {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cinereserve")
                .subject(adminId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .claim("role", "ADMIN")
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

    private Long insertSeat(int seatNumber) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO seat (hall_id, row_label, seat_number)
                VALUES (?, 'A', ?)
                RETURNING id
                """,
                Long.class,
                hallId,
                seatNumber
        );
    }

    private Long insertBooking(Long customerId, Long seatId) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO booking (user_id, showtime_id)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                customerId,
                showtimeId
        );

        jdbcTemplate.update(
                """
                INSERT INTO booking_seat
                    (booking_id, showtime_id, hall_id, seat_id)
                VALUES (?, ?, ?, ?)
                """,
                id,
                showtimeId,
                hallId,
                seatId
        );

        return id;
    }

    @AfterEach
    void tearDown() {
        for (Long id : List.of(
                firstBookingId == null ? -1L : firstBookingId,
                secondBookingId == null ? -1L : secondBookingId
        )) {
            jdbcTemplate.update(
                    "DELETE FROM admin_booking_audit WHERE booking_id = ?",
                    id
            );
            jdbcTemplate.update(
                    "DELETE FROM booking_seat WHERE booking_id = ?",
                    id
            );
            jdbcTemplate.update(
                    "DELETE FROM booking WHERE id = ?",
                    id
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

        for (Long userId : new Long[]{
                firstCustomerId,
                secondCustomerId,
                adminId
        }) {
            if (userId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        userId
                );
            }
        }
    }
}
