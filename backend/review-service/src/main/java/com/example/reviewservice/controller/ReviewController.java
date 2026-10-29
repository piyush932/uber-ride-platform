package com.example.reviewservice.controller;

import com.example.reviewservice.dto.ReviewDtos.CreateReviewRequest;
import com.example.reviewservice.dto.ReviewDtos.DriverRatingSummary;
import com.example.reviewservice.dto.ReviewDtos.ReviewResponse;
import com.example.reviewservice.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@jakarta.validation.Valid @RequestBody CreateReviewRequest request) {
        return service.create(request);
    }

    @GetMapping
    public DriverRatingSummary byDriver(@org.springframework.web.bind.annotation.RequestParam("driverId") Long driverId) {
        return service.byDriver(driverId);
    }
}
