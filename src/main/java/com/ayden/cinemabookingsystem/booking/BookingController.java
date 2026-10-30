package com.ayden.cinemabookingsystem.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse response =
                bookingService.createBooking(request);

        URI location = URI.create(
                "/api/v1/bookings/" + response.id()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @PathVariable Long id
    ) {
        return bookingService.getBookingById(id);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id
    ) {
        return bookingService.cancelBooking(id);
    }
}
