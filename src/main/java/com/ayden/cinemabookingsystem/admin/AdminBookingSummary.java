package com.ayden.cinemabookingsystem.admin;

import com.ayden.cinemabookingsystem.booking.Booking;

import java.time.OffsetDateTime;

public record AdminBookingSummary(
        Long bookingId,
        Long userId,
        String displayName,
        String email,
        Long showtimeId,
        String status,
        OffsetDateTime createdAt
) {
    public static AdminBookingSummary from(Booking booking) {
        return new AdminBookingSummary(
                booking.getId(),
                booking.getUser().getId(),
                booking.getUser().getDisplayName(),
                booking.getUser().getEmail(),
                booking.getShowtime().getId(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
