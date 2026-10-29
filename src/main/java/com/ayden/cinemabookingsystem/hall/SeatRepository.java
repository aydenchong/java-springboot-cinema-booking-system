package com.ayden.cinemabookingsystem.hall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByHallIdOrderByRowLabelAscSeatNumberAsc(Long hallId);

    boolean existsByHallIdAndRowLabelAndSeatNumber(
            Long hallId,
            String rowLabel,
            Integer seatNumber
    );
}
