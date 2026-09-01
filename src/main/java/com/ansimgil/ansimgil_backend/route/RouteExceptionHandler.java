package com.ansimgil.ansimgil_backend.route;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RouteExceptionHandler {
    @ExceptionHandler(RouteException.class)
    public ResponseEntity<Map<String, String>> handleRouteException(RouteException exception) {
        HttpStatus status = switch (exception.type()) {
            case CONFIGURATION, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UPSTREAM -> HttpStatus.BAD_GATEWAY;
        };

        return ResponseEntity.status(status).body(Map.of(
                "error", exception.getMessage()
        ));
    }
}
