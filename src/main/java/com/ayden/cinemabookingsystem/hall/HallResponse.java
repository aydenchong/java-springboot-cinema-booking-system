package com.ayden.cinemabookingsystem.hall;

import java.time.OffsetDateTime;

public record HallResponse(
        Long id,
        String name,
        String hallType,
        OffsetDateTime createdAt
) {
}

