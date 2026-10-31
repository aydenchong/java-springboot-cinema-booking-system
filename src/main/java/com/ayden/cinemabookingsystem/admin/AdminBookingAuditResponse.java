package com.ayden.cinemabookingsystem.admin;

import java.time.OffsetDateTime;

public record AdminBookingAuditResponse(
        Long id,
        Long bookingId,
        Long adminUserId,
        String action,
        String reason,
        OffsetDateTime createdAt
) {
}