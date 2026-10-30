package com.ayden.cinemabookingsystem.booking;

import com.ayden.cinemabookingsystem.hall.Seat;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_seat")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "showtime_id", nullable = false,
            updatable = false)
    private Long showtimeId;

    @Column(name = "hall_id", nullable = false,
            updatable = false)
    private Long hallId;

    @Column(nullable = false, length = 20)
    private String status = "RESERVED";

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    protected BookingSeat() {
    }

    public BookingSeat(Booking booking, Seat seat) {
        if (booking == null || seat == null) {
            throw new IllegalArgumentException(
                    "Booking and seat are required"
            );
        }

        if (booking.getShowtime().getId() == null
                || booking.getShowtime().getHall().getId() == null
                || seat.getHall().getId() == null) {
            throw new IllegalArgumentException(
                    "Showtime, hall and seat must already exist"
            );
        }

        Long showtimeHallId =
                booking.getShowtime().getHall().getId();

        Long seatHallId = seat.getHall().getId();

        if (!showtimeHallId.equals(seatHallId)) {
            throw new IllegalArgumentException(
                    "Seat does not belong to the showtime hall"
            );
        }

        this.booking = booking;
        this.seat = seat;
        this.showtimeId = booking.getShowtime().getId();
        this.hallId = showtimeHallId;
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public Seat getSeat() {
        return seat;
    }

    public Long getShowtimeId() {
        return showtimeId;
    }

    public Long getHallId() {
        return hallId;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void cancel(OffsetDateTime cancelledAt) {
        if (!"RESERVED".equals(status)) {
            throw new IllegalStateException(
                    "Only reserved seats can be cancelled"
            );
        }

        if (cancelledAt == null) {
            throw new IllegalArgumentException(
                    "Cancellation time is required"
            );
        }

        this.status = "CANCELLED";
        this.cancelledAt = cancelledAt;
    }
}
