package com.ayden.cinemabookingsystem.hall;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CinemaHallService {

    private final CinemaHallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final EntityManager entityManager;

    public CinemaHallService(
            CinemaHallRepository hallRepository,
            SeatRepository seatRepository,
            EntityManager entityManager
    ) {
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<HallResponse> getAllHalls() {
        return hallRepository.findAll()
                .stream()
                .map(this::toHallResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HallResponse getHallById(Long hallId) {
        CinemaHall hall = findHallOrThrow(hallId);
        return toHallResponse(hall);
    }

    @Transactional
    public HallResponse createHall(CreateHallRequest request) {

        String name = request.name().trim();

        if (name.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hall name must not be blank"
            );
        }

        if (hallRepository.findByName(name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hall name already exists"
            );
        }

        String hallType = request.hallType() == null
                ? "STANDARD"
                : request.hallType();

        CinemaHall hall = new CinemaHall(name, hallType);

        try {
            CinemaHall savedHall = hallRepository.saveAndFlush(hall);
            entityManager.refresh(savedHall);
            return toHallResponse(savedHall);

        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hall could not be created due to a database constraint",
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsByHallId(Long hallId) {

        findHallOrThrow(hallId);

        return seatRepository
                .findByHallIdOrderByRowLabelAscSeatNumberAsc(hallId)
                .stream()
                .map(this::toSeatResponse)
                .toList();
    }

    @Transactional
    public SeatResponse createSeat(
            Long hallId,
            CreateSeatRequest request
    ) {
        CinemaHall hall = findHallOrThrow(hallId);

        if (seatRepository.existsByHallIdAndRowLabelAndSeatNumber(
                hallId,
                request.rowLabel(),
                request.seatNumber()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat already exists in this hall"
            );
        }

        Seat seat = new Seat(
                hall,
                request.rowLabel(),
                request.seatNumber()
        );

        try {
            Seat savedSeat = seatRepository.saveAndFlush(seat);
            return toSeatResponse(savedSeat);

        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat could not be created due to a database constraint",
                    ex
            );
        }
    }

    private CinemaHall findHallOrThrow(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cinema hall not found"
                ));
    }

    private HallResponse toHallResponse(CinemaHall hall) {
        return new HallResponse(
                hall.getId(),
                hall.getName(),
                hall.getHallType(),
                hall.getCreatedAt()
        );
    }

    private SeatResponse toSeatResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getHall().getId(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatCode()
        );
    }
}

