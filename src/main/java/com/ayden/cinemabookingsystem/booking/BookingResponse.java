package com.ayden.cinemabookingsystem.booking;

import java.time.OffsetDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        Long userId,
        Long showtimeId,
        String movieTitle,
        String hallName,
        List<ReservedSeatResponse> seats,
        String status,
        OffsetDateTime createdAt
) {
    public record ReservedSeatResponse(
            Long seatId,
            String seatCode,
            String status
    ) {
    }
}
