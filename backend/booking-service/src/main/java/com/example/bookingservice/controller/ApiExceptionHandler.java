package com.example.bookingservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring hides the reason of a ResponseStatusException by default, so the client only sees
 * "Conflict". Only the reasons we write ourselves are returned here, never internal error text.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException e) {
        int code = e.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(code);
        String error = status != null ? status.getReasonPhrase() : "Error";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("error", error);
        body.put("message", e.getReason() == null || e.getReason().isBlank() ? error : e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }
}
