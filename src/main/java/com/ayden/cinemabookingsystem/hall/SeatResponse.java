package com.ayden.cinemabookingsystem.hall;

public record SeatResponse(
        Long id,
        Long hallId,
        String rowLabel,
        Integer seatNumber,
        String seatCode
) {
}
