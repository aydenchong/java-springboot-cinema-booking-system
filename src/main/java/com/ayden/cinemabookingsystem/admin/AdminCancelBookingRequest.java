package com.ayden.cinemabookingsystem.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelBookingRequest(

        @NotBlank
        @Size(min = 10, max = 500)
        String reason

) {
}
