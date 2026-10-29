package com.ayden.cinemabookingsystem.hall;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/halls")
public class CinemaHallController {

    private final CinemaHallService hallService;

    public CinemaHallController(CinemaHallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping
    public List<HallResponse> getAllHalls() {
        return hallService.getAllHalls();
    }

    @GetMapping("/{id}")
    public HallResponse getHallById(@PathVariable Long id) {
        return hallService.getHallById(id);
    }

    @PostMapping
    public ResponseEntity<HallResponse> createHall(
            @Valid @RequestBody CreateHallRequest request
    ) {
        HallResponse response = hallService.createHall(request);

        URI location = URI.create(
                "/api/v1/halls/" + response.id()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> getSeats(
            @PathVariable Long id
    ) {
        return hallService.getSeatsByHallId(id);
    }

    @PostMapping("/{id}/seats")
    public ResponseEntity<SeatResponse> createSeat(
            @PathVariable Long id,
            @Valid @RequestBody CreateSeatRequest request
    ) {
        SeatResponse response = hallService.createSeat(id, request);

        URI location = URI.create(
                "/api/v1/halls/" + id + "/seats/" + response.id()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }
}

