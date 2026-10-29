package com.ayden.cinemabookingsystem.showtime;

import com.ayden.cinemabookingsystem.hall.CinemaHall;
import com.ayden.cinemabookingsystem.movie.Movie;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "showtime")
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private CinemaHall hall;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    protected Showtime() {
    }

    public Showtime(
            Movie movie,
            CinemaHall hall,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        this.movie = movie;
        this.hall = hall;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public CinemaHall getHall() {
        return hall;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void cancel() {
        if ("CANCELLED".equals(status)) {
            throw new IllegalStateException(
                    "Showtime is already cancelled"
            );
        }

        status = "CANCELLED";
    }
}

