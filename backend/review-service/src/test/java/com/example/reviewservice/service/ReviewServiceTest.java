package com.example.reviewservice.service;

import com.example.reviewservice.client.BookingClient;
import com.example.reviewservice.client.BookingClient.BookingView;
import com.example.reviewservice.dto.ReviewDtos.CreateReviewRequest;
import com.example.reviewservice.dto.ReviewDtos.DriverRatingSummary;
import com.example.reviewservice.dto.ReviewDtos.ReviewResponse;
import com.example.reviewservice.model.Review;
import com.example.reviewservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository repository;

    @Mock
    private BookingClient bookingClient;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(repository, bookingClient);
    }

    private CreateReviewRequest request() {
        return new CreateReviewRequest(1L, 1L, 5, "Great ride");
    }

    private BookingView completedBooking() {
        return new BookingView(1L, "COMPLETED", 1L, 9001L);
    }

    private Review savedReview(int rating) {
        Review review = Review.builder()
            .bookingId(1L)
            .passengerId(1L)
            .driverId(9001L)
            .rating(rating)
            .comment("ok")
            .build();
        review.setId(1L);
        return review;
    }

    @Test
    void createSavesAReviewForACompletedBooking() {
        when(repository.existsByBookingId(1L)).thenReturn(false);
        when(bookingClient.getBooking(1L)).thenReturn(completedBooking());
        when(repository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReviewResponse response = service.create(request());

        assertEquals(1L, response.id());
        assertEquals(9001L, response.driverId());
        assertEquals(5, response.rating());
    }

    @Test
    void createRejectsABookingThatIsNotCompleted() {
        when(repository.existsByBookingId(1L)).thenReturn(false);
        when(bookingClient.getBooking(1L))
            .thenReturn(new BookingView(1L, "CONFIRMED", 1L, 9001L));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(request()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void createRejectsADuplicateReview() {
        when(repository.existsByBookingId(1L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(request()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void createRejectsAPassengerWhoDidNotMakeTheBooking() {
        when(repository.existsByBookingId(1L)).thenReturn(false);
        when(bookingClient.getBooking(1L)).thenReturn(completedBooking());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(new CreateReviewRequest(1L, 2L, 5, "not my ride")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void driverSummaryComputesTheAverage() {
        when(repository.findByDriverIdOrderByCreatedAtDesc(9001L))
            .thenReturn(List.of(savedReview(5), savedReview(3)));

        DriverRatingSummary summary = service.byDriver(9001L);

        assertEquals(9001L, summary.driverId());
        assertEquals(4.0, summary.averageRating());
        assertEquals(2, summary.totalReviews());
    }

    @Test
    void driverSummaryIsEmptyForAnUnknownDriver() {
        when(repository.findByDriverIdOrderByCreatedAtDesc(9999L)).thenReturn(List.of());

        DriverRatingSummary summary = service.byDriver(9999L);

        assertEquals(0.0, summary.averageRating());
        assertEquals(0, summary.totalReviews());
    }
}
