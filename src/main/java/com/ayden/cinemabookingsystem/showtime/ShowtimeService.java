package com.ayden.cinemabookingsystem.showtime;

import com.ayden.cinemabookingsystem.hall.CinemaHall;
import com.ayden.cinemabookingsystem.hall.CinemaHallRepository;
import com.ayden.cinemabookingsystem.movie.Movie;
import com.ayden.cinemabookingsystem.movie.MovieRepository;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ShowtimeService {

    private static final int CLEANING_MINUTES = 15;

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final CinemaHallRepository hallRepository;
    private final EntityManager entityManager;

    public ShowtimeService(
            ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            CinemaHallRepository hallRepository,
            EntityManager entityManager
    ) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ShowtimeResponse> getAllShowtimes() {
        return showtimeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowtimeResponse getShowtimeById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showtime not found"
                ));

        return toResponse(showtime);
    }

    @Transactional
    public ShowtimeResponse createShowtime(
            CreateShowtimeRequest request
    ) {
        // 1. Find the movie
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Movie not found"
                ));

        // 2. Check movie status
        if (!"ACTIVE".equals(movie.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot schedule an inactive movie"
            );
        }

        // 3. Find the cinema hall
        CinemaHall hall = hallRepository.findById(request.hallId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cinema hall not found"
                ));

        // 4. Validate future start time
        OffsetDateTime startsAt = request.startsAt();

        if (!startsAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Showtime must start in the future"
            );
        }

        // 5. Calculate movie + cleaning duration
        OffsetDateTime endsAt = startsAt.plusMinutes(
                movie.getDurationMinutes() + CLEANING_MINUTES
        );

        // 6. Create the showtime entity
        Showtime showtime = new Showtime(
                movie,
                hall,
                startsAt,
                endsAt
        );

        // 7. Insert into PostgreSQL
        try {
            Showtime saved = showtimeRepository.saveAndFlush(showtime);

            // Retrieve database-generated created_at
            entityManager.refresh(saved);

            return toResponse(saved);

        } catch (DataIntegrityViolationException ex) {

            // PostgreSQL exclusion constraint violation
            if (hasSqlState(ex, "23P01")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Showtime overlaps with an existing schedule",
                        ex
                );
            }

            // Other database violations should not be disguised
            throw ex;
        }
    }

    private boolean hasSqlState(Throwable exception, String sqlState) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SQLException sqlException) {
                if (sqlState.equals(sqlException.getSQLState())) {
                    return true;
                }

                SQLException next = sqlException.getNextException();
                while (next != null) {
                    if (sqlState.equals(next.getSQLState())) {
                        return true;
                    }
                    next = next.getNextException();
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private ShowtimeResponse toResponse(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(),

                showtime.getMovie().getId(),
                showtime.getMovie().getTitle(),

                showtime.getHall().getId(),
                showtime.getHall().getName(),

                showtime.getStartsAt(),
                showtime.getEndsAt(),

                showtime.getStatus(),
                showtime.getCreatedAt()
        );
    }
}
