
package com.ayden.cinemabookingsystem.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void unauthenticatedUser_cannotAccessAdminBookings() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/bookings")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUser_cannotAccessAuditLogs() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/booking-audits")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void customer_cannotAccessAdminBookings() throws Exception {
        String customerToken = createToken(1L, "CUSTOMER");

        mockMvc.perform(
                get("/api/v1/admin/bookings")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + customerToken
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotCancelAnotherUsersBooking() throws Exception {
        String customerToken = createToken(1L, "CUSTOMER");

        mockMvc.perform(
                patch("/api/v1/admin/bookings/999999/cancel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + customerToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Unauthorized cancellation attempt"
                                }
                                """)
        ).andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotReadAdminAuditLogs() throws Exception {
        String customerToken = createToken(1L, "CUSTOMER");

        mockMvc.perform(
                get("/api/v1/admin/booking-audits")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + customerToken
                        )
        ).andExpect(status().isForbidden());
    }

    private String createToken(Long userId, String role) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cinereserve")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.MINUTES))
                .claim("role", role)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
