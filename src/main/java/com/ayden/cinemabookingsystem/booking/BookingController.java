package com.ayden.cinemabookingsystem.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        BookingResponse response =
                bookingService.createBooking(currentUserId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create(
                        "/api/v1/bookings/" + response.id()
                ))
                .body(response);
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        return bookingService.getBookingById(
                id,
                currentUserId
        );
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        return bookingService.cancelBooking(
                id,
                currentUserId
        );
    }
}
