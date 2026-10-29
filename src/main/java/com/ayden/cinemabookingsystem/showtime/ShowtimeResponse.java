package com.ayden.cinemabookingsystem.showtime;

import java.time.OffsetDateTime;

public record ShowtimeResponse(

        Long id,

        Long movieId,
        String movieTitle,

        Long hallId,
        String hallName,

        OffsetDateTime startsAt,
        OffsetDateTime endsAt,

        String status,
        OffsetDateTime createdAt

) {
}
