package com.example.bookingservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsTheReasonStatusAndErrorName() {
        ResponseEntity<Map<String, Object>> response =
            handler.handle(new ResponseStatusException(HttpStatus.CONFLICT, "No drivers nearby"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Conflict", response.getBody().get("error"));
        assertEquals("No drivers nearby", response.getBody().get("message"));
    }

    @Test
    void usesTheStatusNameWhenThereIsNoReason() {
        ResponseEntity<Map<String, Object>> response =
            handler.handle(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Not Found", response.getBody().get("message"));
    }
}
