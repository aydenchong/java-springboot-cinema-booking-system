package com.ayden.cinemabookingsystem.admin;

import com.ayden.cinemabookingsystem.booking.BookingResponse;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(
            AdminBookingService adminBookingService
    ) {
        this.adminBookingService = adminBookingService;
    }

    @GetMapping
    public Page<AdminBookingSummary> searchBookings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String displayName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long adminId = Long.valueOf(jwt.getSubject());

        return adminBookingService.searchBookings(
                adminId,
                userId,
                email,
                displayName,
                page,
                size
        );
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        Long adminId = Long.valueOf(jwt.getSubject());

        return adminBookingService.getBookingById(
                adminId,
                id
        );
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelBookingRequest request
    ) {
        System.out.println("DEBUG: Entered Admin Cancel Controller");

        Long adminId = Long.valueOf(jwt.getSubject());

        return adminBookingService.cancelBooking(
                adminId,
                id,
                request
        );
    }
}
