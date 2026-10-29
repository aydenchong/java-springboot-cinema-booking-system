package com.ayden.cinemabookingsystem.movie;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private EntityManager entityManager;

    private MovieService movieService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        movieService = new MovieService(movieRepository, entityManager);
    }

    @Test
    void getAllMovies_shouldReturnMovieResponses() {
        Movie movie = new Movie(
                "Interstellar",
                "Science fiction",
                169,
                LocalDate.of(2014, 11, 7)
        );

        when(movieRepository.findAll()).thenReturn(List.of(movie));

        List<MovieResponse> result = movieService.getAllMovies();

        assertEquals(1, result.size());
        assertEquals("Interstellar", result.get(0).title());
        assertEquals(169, result.get(0).durationMinutes());

        verify(movieRepository).findAll();
    }

    @Test
    void getMovieById_whenMovieExists_shouldReturnMovie() {
        Movie movie = new Movie(
                "Inception",
                "Science fiction thriller",
                148,
                LocalDate.of(2010, 7, 16)
        );

        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(movie));

        MovieResponse result = movieService.getMovieById(1L);

        assertEquals("Inception", result.title());
        assertEquals(148, result.durationMinutes());

        verify(movieRepository).findById(1L);
    }

    @Test
    void getMovieById_whenMovieDoesNotExist_shouldThrow404() {
        when(movieRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> movieService.getMovieById(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void createMovie_shouldSaveAndReturnMovie() {
        CreateMovieRequest request = new CreateMovieRequest(
                "The Dark Knight",
                "Superhero movie",
                152,
                LocalDate.of(2008, 7, 18)
        );

        Movie movie = new Movie(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.releaseDate()
        );

        when(movieRepository.saveAndFlush(any(Movie.class)))
                .thenReturn(movie);

        MovieResponse result = movieService.createMovie(request);

        assertEquals("The Dark Knight", result.title());
        assertEquals(152, result.durationMinutes());

        verify(movieRepository).saveAndFlush(any(Movie.class));
        verify(entityManager).refresh(movie);
    }
}
