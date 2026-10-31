package com.ayden.cinemabookingsystem.security;

import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.AppUserRepository;
import com.ayden.cinemabookingsystem.user.UserRole;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;


public final class AdminCancellationAuthorization {

    private AdminCancellationAuthorization() {
    }

    public static VerifiedAdmin requireAdmin(
            AppUserRepository userRepository,
            Long adminId
    ) {
        Objects.requireNonNull(userRepository, "userRepository");

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()
                || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
            throw unauthorized();
        }

        Long authenticatedId = parseSubject(jwt.getSubject());

        if (adminId == null || !authenticatedId.equals(adminId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator identity mismatch"
            );
        }

        if (!jwtAuthentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_ADMIN")
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access required"
            );
        }

        AppUser admin = userRepository.findById(authenticatedId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Administrator access required"
                ));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access required"
            );
        }

        return new VerifiedAdmin(authenticatedId);
    }

    private static Long parseSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw unauthorized();
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
        );
    }

    public static final class VerifiedAdmin {

        private final Long id;

        private VerifiedAdmin(Long id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Long id() {
            return id;
        }
    }
}
