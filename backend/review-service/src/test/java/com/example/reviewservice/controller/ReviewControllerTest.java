package com.example.reviewservice.controller;

import com.example.reviewservice.dto.ReviewDtos.CreateReviewRequest;
import com.example.reviewservice.dto.ReviewDtos.DriverRatingSummary;
import com.example.reviewservice.dto.ReviewDtos.ReviewResponse;
import com.example.reviewservice.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service)).build();
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any(CreateReviewRequest.class)))
            .thenReturn(new ReviewResponse(1L, 1L, 1L, 9001L, 5, "Great ride", null));

        mvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":1,\"passengerId\":1,\"rating\":5,\"comment\":\"Great ride\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void createWithRatingZeroReturns400() throws Exception {
        mvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":1,\"passengerId\":1,\"rating\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void driverSummaryReturns200() throws Exception {
        when(service.byDriver(9001L))
            .thenReturn(new DriverRatingSummary(9001L, 5.0, 1,
                List.of(new ReviewResponse(1L, 1L, 1L, 9001L, 5, "ok", null))));

        mvc.perform(get("/api/v1/reviews?driverId=9001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverId").value(9001))
            .andExpect(jsonPath("$.averageRating").value(5.0))
            .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    void unknownBookingReturns404() throws Exception {
        when(service.create(any(CreateReviewRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        mvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":99,\"passengerId\":1,\"rating\":5}"))
            .andExpect(status().isNotFound());
    }
}
