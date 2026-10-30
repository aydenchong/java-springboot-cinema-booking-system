
package com.ayden.cinemabookingsystem.booking;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(
            DataIntegrityViolationException exception
    ) {
        if (!hasSeatReservationConflict(exception)) {
            throw exception;
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of(
                        "status", 409,
                        "error", "Seat already reserved",
                        "message", "One or more seats are no longer available",
                        "timestamp", OffsetDateTime.now().toString()
                )
        );
    }

    private boolean hasSeatReservationConflict(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SQLException sqlException) {

                if ("23505".equals(sqlException.getSQLState())
                        && sqlException.getMessage() != null
                        && sqlException.getMessage().contains(
                        "uq_active_showtime_seat"
                )) {
                    return true;
                }

                SQLException next = sqlException.getNextException();

                while (next != null) {
                    if ("23505".equals(next.getSQLState())
                            && next.getMessage() != null
                            && next.getMessage().contains(
                            "uq_active_showtime_seat"
                    )) {
                        return true;
                    }

                    next = next.getNextException();
                }
            }

            current = current.getCause();
        }

        return false;
    }
}

