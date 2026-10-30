package com.ayden.cinemabookingsystem.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByShowtime_Id(Long showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("""
    SELECT b
    FROM Booking b
    JOIN FETCH b.user
    WHERE b.showtime.id = :showtimeId
    """)
    List<Booking> findByShowtimeWithUser(
            @Param("showtimeId") Long showtimeId
    );
}
