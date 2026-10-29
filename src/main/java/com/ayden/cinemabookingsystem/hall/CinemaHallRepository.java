package com.ayden.cinemabookingsystem.hall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CinemaHallRepository
        extends JpaRepository<CinemaHall, Long> {

    Optional<CinemaHall> findByName(String name);
}
