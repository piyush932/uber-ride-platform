package com.example.bookingservice.event;

import java.time.Instant;

/**
 * Something important happened to a booking. Later phases (review, analytics)
 * react to these instead of being called directly.
 */
public record BookingEvent(
    Long bookingId,
    String type,
    Long passengerId,
    Long driverId,
    String reason,
    Instant occurredAt) {

    public static BookingEvent of(Long bookingId, String type, Long passengerId, Long driverId, String reason) {
        return new BookingEvent(bookingId, type, passengerId, driverId, reason, Instant.now());
    }
}
