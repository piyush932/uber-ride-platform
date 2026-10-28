package com.example.socketservice.dto;

import jakarta.validation.constraints.NotNull;

public final class SocketDtos {

    private SocketDtos() {
    }

    /** Sent by the booking service over REST to tell a driver about a new ride. */
    public record NotifyDriverRequest(
        @NotNull Long driverId,
        @NotNull Long bookingId,
        Long passengerId,
        @NotNull Double pickupLatitude,
        @NotNull Double pickupLongitude) {
    }

    /** Pushed to the driver on /topic/drivers/{driverId}/rides. */
    public record RideRequestMessage(Long bookingId, Long passengerId, Double pickupLatitude, Double pickupLongitude) {
    }

    /** Sent by the driver app to /app/rides/accept. */
    public record AcceptRequest(Long bookingId, Long driverId) {
    }

    /** Pushed on /topic/bookings/{bookingId} so the passenger and driver see the outcome. */
    public record BookingUpdate(Long bookingId, String status, Long driverId, String message) {
    }
}
