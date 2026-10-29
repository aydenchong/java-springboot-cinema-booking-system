package com.ayden.cinemabookingsystem.hall;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHallRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Pattern(regexp = "STANDARD|IMAX|VIP")
        String hallType
) {
}
