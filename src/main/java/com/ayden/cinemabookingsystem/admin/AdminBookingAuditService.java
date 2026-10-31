package com.ayden.cinemabookingsystem.admin;

import com.ayden.cinemabookingsystem.security.AdminCancellationAuthorization;

import com.ayden.cinemabookingsystem.user.AppUserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminBookingAuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AppUserRepository userRepository;

    public AdminBookingAuditService(
            NamedParameterJdbcTemplate jdbcTemplate,
            AppUserRepository userRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminBookingAuditResponse> searchAudits(
            Long adminId,
            Long bookingId,
            Long filterAdminId,
            int page,
            int size
    ) {
        // 1. Verify current administrator identity and role
        AdminCancellationAuthorization.requireAdmin(
                userRepository,
                adminId
        );

        // 2. Validate pagination parameters
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 50"
            );
        }

        // 3. Prepare SQL query parameters
        MapSqlParameterSource params = new MapSqlParameterSource();

        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (bookingId != null) {
            where.append(" AND booking_id = :bookingId");
            params.addValue("bookingId", bookingId);
        }

        if (filterAdminId != null) {
            where.append(" AND admin_user_id = :adminId");
            params.addValue("adminId", filterAdminId);
        }

        // 4. Count matching records
        String countSql =
                "SELECT COUNT(*) FROM admin_booking_audit"
                        + where;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                params,
                Long.class
        );

        // 5. Query records with pagination
        String dataSql = """
                SELECT
                    id,
                    booking_id,
                    admin_user_id,
                    action,
                    reason,
                    created_at
                FROM admin_booking_audit
                """
                + where
                + " ORDER BY created_at DESC, id DESC"
                + " LIMIT :limit OFFSET :offset";

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);

        List<AdminBookingAuditResponse> results =
                jdbcTemplate.query(
                        dataSql,
                        params,
                        (rs, rowNum) -> new AdminBookingAuditResponse(
                                rs.getLong("id"),
                                rs.getLong("booking_id"),
                                rs.getLong("admin_user_id"),
                                rs.getString("action"),
                                rs.getString("reason"),
                                rs.getObject(
                                        "created_at",
                                        java.time.OffsetDateTime.class
                                )
                        )
                );

        // 6. Return Spring Data pagination response
        return new PageImpl<>(
                results,
                PageRequest.of(page, size),
                total == null ? 0 : total
        );
    }
    @Transactional(readOnly = true)
    public AdminBookingAuditResponse getAuditById(
            Long currentAdminId,
            Long auditId
    ) {
        AdminCancellationAuthorization.requireAdmin(
                userRepository,
                currentAdminId
        );

        String sql = """
            SELECT id, booking_id, admin_user_id,
                   action, reason, created_at
            FROM admin_booking_audit
            WHERE id = :auditId
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("auditId", auditId);

        List<AdminBookingAuditResponse> results = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AdminBookingAuditResponse(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getLong("admin_user_id"),
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getObject(
                                "created_at",
                                java.time.OffsetDateTime.class
                        )
                )
        );

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Audit record not found"
            );
        }

        return results.get(0);
    }
}