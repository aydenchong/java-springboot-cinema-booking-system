package com.ayden.cinemabookingsystem.admin;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/booking-audits")
public class AdminBookingAuditController {

    private final AdminBookingAuditService auditService;

    public AdminBookingAuditController(
            AdminBookingAuditService auditService
    ) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminBookingAuditResponse>> searchAudits(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam(required = false)
            Long bookingId,

            @RequestParam(required = false)
            Long adminId,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {
        Long currentAdminId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(
                auditService.searchAudits(
                        currentAdminId,
                        bookingId,
                        adminId,
                        page,
                        size
                )
        );
    }
    @GetMapping("/{id}")
    public ResponseEntity<AdminBookingAuditResponse> getAuditById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        Long currentAdminId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok(
                auditService.getAuditById(currentAdminId, id)
        );
    }
}
