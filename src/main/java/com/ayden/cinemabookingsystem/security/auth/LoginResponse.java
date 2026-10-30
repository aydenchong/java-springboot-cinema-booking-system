package com.ayden.cinemabookingsystem.security.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String role
) {
}
