package com.ayden.cinemabookingsystem.movie;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<MovieResponse> getAllMovies() {
        return movieService.getAllMovies();
    }

    @GetMapping("/{id}")
    public MovieResponse getMovieById(@PathVariable Long id) {
        return movieService.getMovieById(id);
    }

    @PostMapping
    public ResponseEntity<MovieResponse> createMovie(
            @Valid @RequestBody CreateMovieRequest request
    ) {
        MovieResponse response = movieService.createMovie(request);

        URI location = URI.create(
                "/api/v1/movies/" + response.id()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }
}