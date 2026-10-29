package com.example.reviewservice.service;

import com.example.reviewservice.client.BookingClient;
import com.example.reviewservice.client.BookingClient.BookingView;
import com.example.reviewservice.dto.ReviewDtos.CreateReviewRequest;
import com.example.reviewservice.dto.ReviewDtos.DriverRatingSummary;
import com.example.reviewservice.dto.ReviewDtos.ReviewResponse;
import com.example.reviewservice.model.Review;
import com.example.reviewservice.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository repository;
    private final BookingClient bookingClient;

    public ReviewService(ReviewRepository repository, BookingClient bookingClient) {
        this.repository = repository;
        this.bookingClient = bookingClient;
    }

    @Transactional
    public ReviewResponse create(CreateReviewRequest request) {
        if (repository.existsByBookingId(request.bookingId())) {
            throw new ResponseStatusException(CONFLICT, "This booking has already been reviewed");
        }

        BookingView booking = bookingClient.getBooking(request.bookingId());

        if (!"COMPLETED".equals(booking.status())) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.status() + ", it must be COMPLETED before it can be reviewed");
        }
        if (!request.passengerId().equals(booking.passengerId())) {
            throw new ResponseStatusException(FORBIDDEN, "Only the passenger of this booking can review it");
        }

        Review review = repository.save(Review.builder()
            .bookingId(request.bookingId())
            .passengerId(request.passengerId())
            .driverId(booking.driverId())
            .rating(request.rating())
            .comment(request.comment())
            .build());

        log.info("REVIEW CREATED booking={} passenger={} driver={} rating={}",
            review.getBookingId(), review.getPassengerId(), review.getDriverId(), review.getRating());
        return ReviewResponse.from(review);
    }

    public DriverRatingSummary byDriver(Long driverId) {
        List<Review> reviews = repository.findByDriverIdOrderByCreatedAtDesc(driverId);
        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        double rounded = Math.round(average * 100.0) / 100.0;
        return new DriverRatingSummary(
            driverId,
            rounded,
            reviews.size(),
            reviews.stream().map(ReviewResponse::from).toList());
    }
}
