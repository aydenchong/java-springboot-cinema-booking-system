package com.ayden.cinemabookingsystem.booking;

import com.ayden.cinemabookingsystem.hall.Seat;
import com.ayden.cinemabookingsystem.hall.SeatRepository;
import com.ayden.cinemabookingsystem.showtime.Showtime;
import com.ayden.cinemabookingsystem.showtime.ShowtimeRepository;
import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.AppUserRepository;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final AppUserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final EntityManager entityManager;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            AppUserRepository userRepository,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            EntityManager entityManager
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.userRepository = userRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public BookingResponse createBooking(
            Long currentUserId,
            CreateBookingRequest request
    ) {

        // 1. Reject repeated seat IDs in one request
        if (request.seatIds().size()
                != new HashSet<>(request.seatIds()).size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate seat IDs in request"
            );
        }

        // 2. Find user
        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        // 3. Find showtime
        Showtime showtime = showtimeRepository
                .findById(request.showtimeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showtime not found"
                ));

        // 4. Check showtime availability
        if (!"SCHEDULED".equals(showtime.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Showtime is not available for booking"
            );
        }

        if (!showtime.getStartsAt().isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking is closed for this showtime"
            );
        }

        // 5. Load requested seats
        List<Seat> seats = seatRepository.findAllById(
                request.seatIds()
        );

        if (seats.size() != request.seatIds().size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more seats do not exist"
            );
        }

        // 6. Confirm all seats belong to the showtime hall
        Long showtimeHallId = showtime.getHall().getId();

        for (Seat seat : seats) {
            if (!showtimeHallId.equals(seat.getHall().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Seat does not belong to showtime hall"
                );
            }
        }

        // 7. Create booking
        Booking booking = new Booking(user, showtime);
        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // 8. Create all reserved seats
        List<BookingSeat> bookingSeats = new ArrayList<>();

        for (Seat seat : seats) {
            bookingSeats.add(new BookingSeat(savedBooking, seat));
        }

        List<BookingSeat> savedSeats =
                bookingSeatRepository.saveAllAndFlush(bookingSeats);

        // 9. Retrieve database-generated timestamp
        entityManager.refresh(savedBooking);

        // 10. Prepare response in requested seat order
        Map<Long, BookingSeat> bySeatId = savedSeats.stream()
                .collect(Collectors.toMap(
                        bs -> bs.getSeat().getId(),
                        Function.identity()
                ));

        List<BookingResponse.ReservedSeatResponse> seatResponses =
                request.seatIds().stream()
                        .map(id -> {
                            BookingSeat bs = bySeatId.get(id);

                            return new BookingResponse.ReservedSeatResponse(
                                    id,
                                    bs.getSeat().getSeatCode(),
                                    bs.getStatus()
                            );
                        })
                        .toList();

        return new BookingResponse(
                savedBooking.getId(),
                user.getId(),
                showtime.getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getName(),
                seatResponses,
                savedBooking.getStatus(),
                savedBooking.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(
            Long id,
            Long currentUserId
    ) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found"
                ));

        List<BookingSeat> bookingSeats =
                bookingSeatRepository.findByBooking_Id(id);

        if (!booking.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not own this booking"
            );
        }
        List<BookingResponse.ReservedSeatResponse> seats =
                bookingSeats.stream()
                        .map(bs -> new BookingResponse.ReservedSeatResponse(
                                bs.getSeat().getId(),
                                bs.getSeat().getSeatCode(),
                                bs.getStatus()
                        ))
                        .sorted((a, b) ->
                                a.seatCode().compareTo(b.seatCode()))
                        .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getShowtime().getId(),
                booking.getShowtime().getMovie().getTitle(),
                booking.getShowtime().getHall().getName(),
                seats,
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }

    @Transactional
    public BookingResponse cancelBooking(
            Long bookingId,
            Long currentUserId
    ) {
        // 1. Lock the booking row
        Booking booking = bookingRepository
                .findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found"
                ));

        if (!booking.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not own this booking"
            );
        }

        // 2. Reject already cancelled bookings
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking is already cancelled"
            );
        }

        // 3. Do not allow cancellation after showtime starts
        if (!booking.getShowtime().getStartsAt()
                .isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot cancel a booking after the showtime starts"
            );
        }

        // 4. Load all reserved seats belonging to this booking
        List<BookingSeat> bookingSeats =
                bookingSeatRepository.findByBooking_Id(bookingId);

        if (bookingSeats.isEmpty()) {
            throw new IllegalStateException(
                    "Confirmed booking has no reserved seats"
            );
        }

        for (BookingSeat bookingSeat : bookingSeats) {
            if (!"RESERVED".equals(bookingSeat.getStatus())) {
                throw new IllegalStateException(
                        "Confirmed booking has a non-reserved seat"
                );
            }
        }

        // 5. Use the same cancellation timestamp
        OffsetDateTime cancelledAt = OffsetDateTime.now();

        // 6. Cancel the parent booking
        booking.cancel(cancelledAt);

        // 7. Cancel every reserved seat
        for (BookingSeat bookingSeat : bookingSeats) {
            bookingSeat.cancel(cancelledAt);
        }

        // 8. Flush changes to PostgreSQL
        bookingSeatRepository.flush();
        bookingRepository.flush();

        // 9. Build the response
        List<BookingResponse.ReservedSeatResponse> seatResponses =
                bookingSeats.stream()
                        .map(bs ->
                                new BookingResponse.ReservedSeatResponse(
                                        bs.getSeat().getId(),
                                        bs.getSeat().getSeatCode(),
                                        bs.getStatus()
                                )
                        )
                        .sorted((a, b) ->
                                a.seatCode().compareTo(b.seatCode()))
                        .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getShowtime().getId(),
                booking.getShowtime().getMovie().getTitle(),
                booking.getShowtime().getHall().getName(),
                seatResponses,
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
