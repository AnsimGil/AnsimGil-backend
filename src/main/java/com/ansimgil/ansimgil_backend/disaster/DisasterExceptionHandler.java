package com.ansimgil.ansimgil_backend.disaster;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class DisasterExceptionHandler {
    @ExceptionHandler(DisasterException.class)
    public ResponseEntity<Map<String, String>> handleDisasterException(DisasterException exception) {
        HttpStatus status = exception.type() == DisasterException.Type.UPSTREAM
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status).body(Map.of(
                "error", exception.getMessage()
        ));
    }
}
