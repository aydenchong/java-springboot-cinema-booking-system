package com.ayden.cinemabookingsystem.hall;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cinema_hall")
public class CinemaHall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "hall_type", nullable = false, length = 20)
    private String hallType = "STANDARD";

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    protected CinemaHall() {
    }

    public CinemaHall(String name, String hallType) {
        this.name = name;
        this.hallType = hallType;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHallType() {
        return hallType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
