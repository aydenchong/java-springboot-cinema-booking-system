package com.ayden.cinemabookingsystem.admin;

import com.ayden.cinemabookingsystem.booking.Booking;
import com.ayden.cinemabookingsystem.booking.BookingRepository;
import com.ayden.cinemabookingsystem.booking.BookingResponse;
import com.ayden.cinemabookingsystem.booking.BookingService;
import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.AppUserRepository;
import com.ayden.cinemabookingsystem.user.UserRole;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final AppUserRepository userRepository;
    private final BookingService bookingService;

    public AdminBookingService(
            BookingRepository bookingRepository,
            AppUserRepository userRepository,
            BookingService bookingService
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
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

    private void verifyAdmin(Long adminId) {

        AppUser admin = userRepository.findById(adminId)
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
    }
}
