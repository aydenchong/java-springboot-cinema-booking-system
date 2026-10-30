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
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth

                        // Public authentication endpoints
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Public read endpoints
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

                        // All other endpoints require a valid JWT
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {})
                )

                .exceptionHandling(exceptions -> exceptions

                        .authenticationEntryPoint(
                                (request, response, ex) ->
                                        response.sendError(
                                                HttpStatus.UNAUTHORIZED.value(),
                                                "Authentication required"
                                        )
                        )

                        .accessDeniedHandler(
                                (request, response, ex) ->
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
