package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingDtos.AcceptBookingRequest;
import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CancelBookingRequest;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    public BookingResponse create(
            @Valid @RequestBody CreateBookingRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(
                value = "Idempotency-Key", required = false) String idempotencyKey) {

        BookingResponse response = service.create(request, idempotencyKey);
        // A retried request with the same key returns the existing booking with 200.
        return response;
    }

    @PostMapping("/{id}/accept")
    public BookingResponse accept(@PathVariable("id") Long id, @Valid @RequestBody AcceptBookingRequest request) {
        return service.accept(id, request);
    }

    @PostMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable("id") Long id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable("id") Long id, @RequestBody(required = false) CancelBookingRequest request) {
        return service.cancel(id, request == null ? null : request.reason());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<BookingResponse> byPassenger(@RequestParam("passengerId") Long passengerId) {
        return service.byPassenger(passengerId);
    }
}
