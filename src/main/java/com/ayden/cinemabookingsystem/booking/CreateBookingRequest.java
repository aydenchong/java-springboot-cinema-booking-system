package com.ayden.cinemabookingsystem.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(

        @NotNull
        @Positive
        Long showtimeId,

        @NotEmpty
        @Size(max = 6)
        List<@NotNull @Positive Long> seatIds
) {
}
