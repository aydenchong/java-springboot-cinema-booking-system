package com.ayden.cinemabookingsystem.movie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateMovieRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        String description,

        @NotNull
        @Positive
        Integer durationMinutes,

        LocalDate releaseDate
) {
}