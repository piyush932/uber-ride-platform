package com.example.bookingservice.dto;

import com.example.bookingservice.dto.BookingDtos.BookingResponse;

import java.time.Instant;
import java.util.List;

public final class DriverDtos {

    private DriverDtos() {
    }

    /** secondsRemaining lets the browser count down without trusting its own clock. */
    public record OfferView(
        Long id,
        Long bookingId,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropLatitude,
        Double dropLongitude,
        Instant expiresAt,
        long secondsRemaining) {
    }

    public record DashboardResponse(BookingResponse activeRide, List<OfferView> offers) {
    }
}
