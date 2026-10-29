package com.ayden.cinemabookingsystem.hall;

import jakarta.persistence.*;

@Entity
@Table(
        name = "seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seat_hall_position",
                columnNames = {"hall_id", "row_label", "seat_number"}
        )
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private CinemaHall hall;

    @Column(name = "row_label", nullable = false, length = 5)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    protected Seat() {
    }

    public Seat(CinemaHall hall, String rowLabel, Integer seatNumber) {
        this.hall = hall;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
    }

    public Long getId() {
        return id;
    }

    public CinemaHall getHall() {
        return hall;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public String getSeatCode() {
        return rowLabel + seatNumber;
    }
}

