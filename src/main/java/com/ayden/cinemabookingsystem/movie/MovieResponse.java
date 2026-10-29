package com.ayden.cinemabookingsystem.movie;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MovieResponse(
        Long id,
        String title,
        String description,
        Integer durationMinutes,
        LocalDate releaseDate,
        String status,
        OffsetDateTime createdAt
) {
}