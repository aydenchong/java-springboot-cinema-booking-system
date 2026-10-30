package com.ayden.cinemabookingsystem.booking;

import com.ayden.cinemabookingsystem.showtime.Showtime;
import com.ayden.cinemabookingsystem.user.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Column(nullable = false, length = 20)
    private String status = "CONFIRMED";

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    protected Booking() {
    }

    public Booking(AppUser user, Showtime showtime) {
        this.user = user;
        this.showtime = showtime;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Showtime getShowtime() {
        return showtime;
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
        if (!"CONFIRMED".equals(status)) {
            throw new IllegalStateException(
                    "Only confirmed bookings can be cancelled"
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

