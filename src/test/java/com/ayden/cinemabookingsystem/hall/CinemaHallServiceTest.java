package com.ayden.cinemabookingsystem.hall;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CinemaHallServiceTest {

    private CinemaHallRepository hallRepository;
    private SeatRepository seatRepository;
    private EntityManager entityManager;
    private CinemaHallService hallService;

    @BeforeEach
    void setUp() {
        hallRepository = mock(CinemaHallRepository.class);
        seatRepository = mock(SeatRepository.class);
        entityManager = mock(EntityManager.class);

        hallService = new CinemaHallService(
                hallRepository,
                seatRepository,
                entityManager
        );
    }

    @Test
    void createHall_shouldSaveSuccessfully() {
        CreateHallRequest request =
                new CreateHallRequest("Hall 1", "STANDARD");

        when(hallRepository.findByName("Hall 1"))
                .thenReturn(Optional.empty());

        when(hallRepository.saveAndFlush(any(CinemaHall.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallResponse response = hallService.createHall(request);

        assertEquals("Hall 1", response.name());
        assertEquals("STANDARD", response.hallType());

        verify(hallRepository).saveAndFlush(any(CinemaHall.class));
        verify(entityManager).refresh(any(CinemaHall.class));
    }

    @Test
    void createHall_whenNameExists_shouldThrow409() {
        CreateHallRequest request =
                new CreateHallRequest("Hall 1", "STANDARD");

        when(hallRepository.findByName("Hall 1"))
                .thenReturn(Optional.of(
                        new CinemaHall("Hall 1", "STANDARD")
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> hallService.createHall(request)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(hallRepository, never())
                .saveAndFlush(any(CinemaHall.class));
    }

    @Test
    void createSeat_shouldSaveSuccessfully() {
        CinemaHall hall =
                new CinemaHall("Hall 1", "STANDARD");

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(hall));

        when(seatRepository
                .existsByHallIdAndRowLabelAndSeatNumber(1L, "A", 1))
                .thenReturn(false);

        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateSeatRequest request =
                new CreateSeatRequest("A", 1);

        SeatResponse response = hallService.createSeat(1L, request);

        assertEquals("A1", response.seatCode());
        assertEquals("A", response.rowLabel());
        assertEquals(1, response.seatNumber());

        verify(seatRepository).saveAndFlush(any(Seat.class));
    }

    @Test
    void createSeat_whenDuplicate_shouldThrow409() {
        CinemaHall hall =
                new CinemaHall("Hall 1", "STANDARD");

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(hall));

        when(seatRepository
                .existsByHallIdAndRowLabelAndSeatNumber(1L, "A", 1))
                .thenReturn(true);

        CreateSeatRequest request =
                new CreateSeatRequest("A", 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> hallService.createSeat(1L, request)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(seatRepository, never())
                .saveAndFlush(any(Seat.class));
    }

    @Test
    void getHallById_whenMissing_shouldThrow404() {
        when(hallRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> hallService.getHallById(999L)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void getSeatsByHallId_shouldReturnOrderedSeats() {
        CinemaHall hall =
                new CinemaHall("Hall 1", "STANDARD");

        Seat seat1 = new Seat(hall, "A", 1);
        Seat seat2 = new Seat(hall, "A", 2);

        when(hallRepository.findById(1L))
                .thenReturn(Optional.of(hall));

        when(seatRepository
                .findByHallIdOrderByRowLabelAscSeatNumberAsc(1L))
                .thenReturn(List.of(seat1, seat2));

        List<SeatResponse> result =
                hallService.getSeatsByHallId(1L);

        assertEquals(2, result.size());
        assertEquals("A1", result.get(0).seatCode());
        assertEquals("A2", result.get(1).seatCode());
    }
}
