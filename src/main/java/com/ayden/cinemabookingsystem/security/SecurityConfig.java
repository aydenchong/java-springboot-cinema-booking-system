package com.ayden.cinemabookingsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) {
        http
                // Stateless REST API using Bearer tokens later.
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Disable browser login mechanisms.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // Configure access rules.
                .authorizeHttpRequests(auth -> auth

                        // Public read-only endpoints
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/movies",
                                "/api/v1/movies/*",
                                "/api/v1/halls",
                                "/api/v1/halls/*",
                                "/api/v1/halls/*/seats",
                                "/api/v1/showtimes",
                                "/api/v1/showtimes/*"
                        ).permitAll()

                        // Everything else requires authentication.
                        .anyRequest().authenticated()
                )

                // Return proper REST API status codes.
                .exceptionHandling(exceptions -> exceptions

                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        response.sendError(
                                                HttpStatus.UNAUTHORIZED.value(),
                                                "Authentication required"
                                        )
                        )

                        .accessDeniedHandler(
                                (request, response, deniedException) ->
                                        response.sendError(
                                                HttpStatus.FORBIDDEN.value(),
                                                "Access denied"
                                        )
                        )
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
