package com.example.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Booking lives in the shared entity-service library; IdempotencyRecord and RideOffer live in this service.
// Both packages must be scanned or JPA will not manage the local entities.
@EntityScan({
    "com.example.entityservice.models",
    "com.example.bookingservice.model"
})
// Fills createdAt / updatedAt from BaseModel.
@EnableJpaAuditing
// Runs OfferExpiryJob.
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
