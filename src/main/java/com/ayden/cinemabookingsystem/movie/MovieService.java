package com.ayden.cinemabookingsystem.movie;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final EntityManager entityManager;

    public MovieService(
            MovieRepository movieRepository,
            EntityManager entityManager
    ) {
        this.movieRepository = movieRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> getAllMovies() {
        return movieRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MovieResponse getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Movie not found"
                ));

        return toResponse(movie);
    }

    @Transactional
    public MovieResponse createMovie(CreateMovieRequest request) {
        Movie movie = new Movie(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.releaseDate()
        );

        Movie savedMovie = movieRepository.saveAndFlush(movie);

        // Retrieve database-generated created_at value
        entityManager.refresh(savedMovie);

        return toResponse(savedMovie);
    }

    private MovieResponse toResponse(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getReleaseDate(),
                movie.getStatus(),
                movie.getCreatedAt()
        );
    }
}