package com.ansimgil.ansimgil_backend.place;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PlaceExceptionHandler {
    @ExceptionHandler(PlaceException.class)
    public ResponseEntity<Map<String, String>> handlePlaceException(PlaceException exception) {
        HttpStatus status = switch (exception.type()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UPSTREAM -> HttpStatus.BAD_GATEWAY;
            case CONFIGURATION, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}
