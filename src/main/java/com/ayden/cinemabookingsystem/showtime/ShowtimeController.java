package com.ayden.cinemabookingsystem.showtime;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    public List<ShowtimeResponse> getAllShowtimes() {
        return showtimeService.getAllShowtimes();
    }

    @GetMapping("/{id}")
    public ShowtimeResponse getShowtimeById(
            @PathVariable Long id
    ) {
        return showtimeService.getShowtimeById(id);
    }

    @PostMapping
    public ResponseEntity<ShowtimeResponse> createShowtime(
            @Valid @RequestBody CreateShowtimeRequest request
    ) {
        ShowtimeResponse response =
                showtimeService.createShowtime(request);

        URI location = URI.create(
                "/api/v1/showtimes/" + response.id()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }
}
