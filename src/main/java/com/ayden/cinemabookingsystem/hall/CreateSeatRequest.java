package com.ayden.cinemabookingsystem.hall;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateSeatRequest(
        @Pattern(regexp = "[A-Z]{1,5}")
        @NotNull
        String rowLabel,

        @NotNull
        @Positive
        Integer seatNumber
) {
}
