package com.ayden.cinemabookingsystem.showtime;

import com.ayden.cinemabookingsystem.hall.CinemaHall;
import com.ayden.cinemabookingsystem.hall.CinemaHallRepository;
import com.ayden.cinemabookingsystem.movie.Movie;
import com.ayden.cinemabookingsystem.movie.MovieRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShowtimeServiceTest {

    private ShowtimeRepository showtimeRepository;
    private MovieRepository movieRepository;
    private CinemaHallRepository hallRepository;
    private EntityManager entityManager;

    private ShowtimeService showtimeService;

    @BeforeEach
    void setUp() {
        showtimeRepository = mock(ShowtimeRepository.class);
        movieRepository = mock(MovieRepository.class);
        hallRepository = mock(CinemaHallRepository.class);
        entityManager = mock(EntityManager.class);

        showtimeService = new ShowtimeService(
                showtimeRepository,
                movieRepository,
                hallRepository,
                entityManager
        );
    }

    private Movie activeMovie() {
        return new Movie(
                "Interstellar",
                "Science fiction",
                169,
                LocalDate.of(2014, 11, 7)
        );
    }

    private CreateShowtimeRequest validRequest() {
        return new CreateShowtimeRequest(
                1L,
                1L,
                OffsetDateTime.now().plusDays(7)
        );
    }

    @Test
    void createShowtime_whenMovieMissing_shouldReturn404() {
        when(movieRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> showtimeService.createShowtime(validRequest())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(showtimeRepository);
    }

    @Test
    void createShowtime_whenHallMissing_shouldReturn404() {
        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(activeMovie()));

        when(hallRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> showtimeService.createShowtime(validRequest())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(showtimeRepository);
    }

    @Test
    void createShowtime_whenMovieInactive_shouldReturn400() {
        Movie movie = activeMovie();
        movie.deactivate();

        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(movie));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> showtimeService.createShowtime(validRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(showtimeRepository);
    }

    @Test
    void createShowtime_whenStartTimeInPast_shouldReturn400() {
        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(activeMovie()));

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(
                        new CinemaHall("Hall 1", "STANDARD")
                ));

        CreateShowtimeRequest request = new CreateShowtimeRequest(
                1L,
                1L,
                OffsetDateTime.now().minusDays(1)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> showtimeService.createShowtime(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(showtimeRepository);
    }

    @Test
    void createShowtime_shouldCalculateEndTimeCorrectly() {
        Movie movie = activeMovie();
        CinemaHall hall = new CinemaHall("Hall 1", "STANDARD");

        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(movie));

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(hall));

        when(showtimeRepository.saveAndFlush(any(Showtime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateShowtimeRequest request = validRequest();

        showtimeService.createShowtime(request);

        ArgumentCaptor<Showtime> captor =
                ArgumentCaptor.forClass(Showtime.class);

        verify(showtimeRepository).saveAndFlush(captor.capture());

        Showtime savedShowtime = captor.getValue();

        assertEquals(request.startsAt(), savedShowtime.getStartsAt());

        assertEquals(
                request.startsAt().plusMinutes(184),
                savedShowtime.getEndsAt()
        );

        assertEquals("SCHEDULED", savedShowtime.getStatus());

        verify(entityManager).refresh(savedShowtime);
    }

    @Test
    void createShowtime_whenDatabaseOverlap_shouldReturn409() {
        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(activeMovie()));

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(
                        new CinemaHall("Hall 1", "STANDARD")
                ));

        SQLException sqlException = new SQLException(
                "Exclusion constraint violation",
                "23P01"
        );

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "Showtime overlap",
                        sqlException
                );

        when(showtimeRepository.saveAndFlush(any(Showtime.class)))
                .thenThrow(databaseException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> showtimeService.createShowtime(validRequest())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(entityManager, never()).refresh(any());
    }
}

