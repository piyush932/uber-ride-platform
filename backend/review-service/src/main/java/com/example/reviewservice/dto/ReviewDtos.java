package com.example.reviewservice.dto;

import com.example.reviewservice.model.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReviewRequest(
        @NotNull Long bookingId,
        @NotNull Long passengerId,
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment) {
    }

    public record ReviewResponse(
        Long id,
        Long bookingId,
        Long passengerId,
        Long driverId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {

        public static ReviewResponse from(Review review) {
            return new ReviewResponse(
                review.getId(),
                review.getBookingId(),
                review.getPassengerId(),
                review.getDriverId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
        }
    }

    public record DriverRatingSummary(
        Long driverId,
        double averageRating,
        int totalReviews,
        List<ReviewResponse> reviews) {
    }
}
