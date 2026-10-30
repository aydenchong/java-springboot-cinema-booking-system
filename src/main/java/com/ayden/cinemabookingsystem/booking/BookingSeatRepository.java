package com.ayden.cinemabookingsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository
        extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBooking_Id(Long bookingId);

    List<BookingSeat> findByShowtimeIdAndStatus(
            Long showtimeId,
            String status
    );
}
