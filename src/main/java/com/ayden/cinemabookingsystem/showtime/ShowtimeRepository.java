package com.ayden.cinemabookingsystem.showtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ShowtimeRepository
        extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovie_IdOrderByStartsAtAsc(Long movieId);

    List<Showtime> findByHall_IdOrderByStartsAtAsc(Long hallId);

    List<Showtime> findByStartsAtGreaterThanEqualOrderByStartsAtAsc(
            OffsetDateTime startsAt
    );
}
