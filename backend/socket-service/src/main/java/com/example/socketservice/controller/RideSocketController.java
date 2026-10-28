package com.example.socketservice.controller;

import com.example.socketservice.dto.SocketDtos.AcceptRequest;
import com.example.socketservice.dto.SocketDtos.BookingUpdate;
import com.example.socketservice.service.BookingClient;
import com.example.socketservice.service.SocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Handles messages that driver apps send over the WebSocket. */
@Controller
public class RideSocketController {

    private static final Logger log = LoggerFactory.getLogger(RideSocketController.class);

    private final BookingClient bookingClient;
    private final SocketService socketService;

    public RideSocketController(BookingClient bookingClient, SocketService socketService) {
        this.bookingClient = bookingClient;
        this.socketService = socketService;
    }

    @MessageMapping("/rides/accept")
    public void accept(AcceptRequest request) {
        if (request == null || request.bookingId() == null || request.driverId() == null) {
            log.warn("Ignoring accept message without bookingId or driverId");
            return;
        }

        boolean confirmed = bookingClient.accept(request.bookingId(), request.driverId());
        String status = confirmed ? "CONFIRMED" : "ACCEPT_FAILED";
        String message = confirmed ? "Driver accepted the ride" : "Booking service could not confirm the ride";
        socketService.publishBookingUpdate(new BookingUpdate(request.bookingId(), status, request.driverId(), message));
    }
}
