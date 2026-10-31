package com.ayden.cinemabookingsystem.admin;

import com.ayden.cinemabookingsystem.booking.Booking;
import com.ayden.cinemabookingsystem.booking.BookingRepository;
import com.ayden.cinemabookingsystem.booking.BookingResponse;
import com.ayden.cinemabookingsystem.booking.BookingService;
import com.ayden.cinemabookingsystem.security.AdminCancellationAuthorization;
import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.AppUserRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final AppUserRepository userRepository;
    private final BookingService bookingService;
    private final JdbcTemplate jdbcTemplate;

    public AdminBookingService(
            BookingRepository bookingRepository,
            AppUserRepository userRepository,
            BookingService bookingService,
            JdbcTemplate jdbcTemplate
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Page<AdminBookingSummary> searchBookings(
            Long adminId,
            Long userId,
            String email,
            String displayName,
            int page,
            int size
    ) {
        verifyAdmin(adminId);

        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 50"
            );
        }

        Specification<Booking> specification =
                (root, query, cb) -> {

                    List<Predicate> filters = new ArrayList<>();

                    Join<Booking, AppUser> user =
                            root.join("user");

                    if (userId != null) {
                        filters.add(
                                cb.equal(user.get("id"), userId)
                        );
                    }

                    if (email != null && !email.isBlank()) {
                        String normalizedEmail =
                                email.trim().toLowerCase(Locale.ROOT);

                        filters.add(
                                cb.equal(
                                        cb.lower(user.get("email")),
                                        normalizedEmail
                                )
                        );
                    }

                    if (displayName != null &&
                            !displayName.isBlank()) {

                        String name = displayName.trim()
                                .toLowerCase(Locale.ROOT);

                        String escaped = name
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_");

                        filters.add(
                                cb.like(
                                        cb.lower(user.get("displayName")),
                                        "%" + escaped + "%",
                                        '\\'
                                )
                        );
                    }

                    return cb.and(
                            filters.toArray(new Predicate[0])
                    );
                };

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return bookingRepository
                .findAll(specification, pageable)
                .map(AdminBookingSummary::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(
            Long adminId,
            Long bookingId
    ) {
        verifyAdmin(adminId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found"
                ));

        // Reuse the existing booking detail mapping.
        // The trusted caller here is AdminBookingService,
        // which has already verified the database ADMIN role.
        return bookingService.getBookingById(
                bookingId,
                booking.getUser().getId()
        );
    }

    private AdminCancellationAuthorization.VerifiedAdmin verifyAdmin(
            Long adminId
    ) {
        return AdminCancellationAuthorization.requireAdmin(
                userRepository,
                adminId
        );
    }

    @Transactional
    public BookingResponse cancelBooking(
            Long adminId,
            Long bookingId,
            AdminCancelBookingRequest request
    ) {
        // 1. Verify current ADMIN role in PostgreSQL
        AdminCancellationAuthorization.VerifiedAdmin verifiedAdmin =
                verifyAdmin(adminId);
        // 2. Validate and normalize the reason
        String reason = request.reason().trim();

        if (reason.length() < 10 || reason.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancellation reason must contain 10 to 500 characters"
            );
        }

        // 3. Reuse the shared cancellation rules without fabricating
        // a customer identity from the booking owner. The opaque capability
        // proves that the current database ADMIN check already passed.
        BookingResponse response =
                bookingService.cancelBookingForVerifiedAdmin(
                        bookingId,
                        verifiedAdmin
                );

        // 4. Record the ADMIN action in the same transaction
        jdbcTemplate.update(
                """
                INSERT INTO admin_booking_audit
                    (booking_id, admin_user_id, action, reason)
                VALUES (?, ?, 'CANCEL', ?)
                """,
                bookingId,
                verifiedAdmin.id(),
                reason
        );

        return response;
    }
}
