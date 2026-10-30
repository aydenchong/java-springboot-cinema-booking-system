package com.ayden.cinemabookingsystem.security.auth;

import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.UserRole;

import java.time.OffsetDateTime;

public record RegisterResponse(
        Long id,
        String displayName,
        String email,
        UserRole role,
        OffsetDateTime createdAt
) {
    public static RegisterResponse from(AppUser user) {
        return new RegisterResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}

