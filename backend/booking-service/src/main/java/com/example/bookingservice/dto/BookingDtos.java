package com.example.bookingservice.dto;

import com.example.entityservice.models.Booking;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
        @NotNull Long passengerId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLongitude,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double dropLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double dropLongitude) {
    }

    public record AcceptBookingRequest(@NotNull Long driverId) {
    }

    public record CancelBookingRequest(String reason) {
    }

    /**
     * candidateDriverId is the driver we picked and pushed the ride to.
     * driverId is only set once that driver accepts.
     */
    public record BookingResponse(
        Long id,
        String status,
        Long passengerId,
        Long driverId,
        Long candidateDriverId,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropLatitude,
        Double dropLongitude,
        String cancelReason,
        LocalDateTime createdAt) {

        public static BookingResponse from(Booking booking, Long candidateDriverId) {
            return new BookingResponse(
                booking.getId(),
                booking.getStatus().name(),
                booking.getPassengerId(),
                booking.getDriverId(),
                candidateDriverId,
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDropLatitude(),
                booking.getDropLongitude(),
                booking.getCancelReason(),
                booking.getCreatedAt());
        }
    }
}
