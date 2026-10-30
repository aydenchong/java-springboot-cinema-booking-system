package com.ayden.cinemabookingsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByShowtime_Id(Long showtimeId);
}
