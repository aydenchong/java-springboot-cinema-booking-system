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

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

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
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 1. Public authentication
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // 2. Public read endpoints
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

                        // 3. Admin management endpoints
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/movies",
                                "/api/v1/halls",
                                "/api/v1/halls/*/seats",
                                "/api/v1/showtimes"
                        ).hasRole("ADMIN")

                        // Admin booking search and detail
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/admin/bookings",
                                "/api/v1/admin/bookings/*"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/admin/bookings/{id}/cancel"
                        ).hasRole("ADMIN")

                        // Deny other admin APIs until implemented
                        .requestMatchers("/api/v1/admin/**")
                        .denyAll()

                        // 5. Booking endpoints require JWT
                        .requestMatchers("/api/v1/bookings/**")
                        .authenticated()

                        // 6. Deny everything not explicitly allowed
                        .anyRequest().denyAll()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
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
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authorities =
                new JwtGrantedAuthoritiesConverter();

        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
