package com.ayden.cinemabookingsystem.showtime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record CreateShowtimeRequest(

        @NotNull
        @Positive
        Long movieId,

        @NotNull
        @Positive
        Long hallId,

        @NotNull
        OffsetDateTime startsAt

) {
}
