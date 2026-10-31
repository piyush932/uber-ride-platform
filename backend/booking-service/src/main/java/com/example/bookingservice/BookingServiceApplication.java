package com.example.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
// Booking lives in the shared entity-service library; IdempotencyRecord lives in this service.
// Both packages must be scanned or JPA will not manage the local entity.
@EntityScan({
    "com.example.entityservice.models",
    "com.example.bookingservice.model"
})
// Fills createdAt / updatedAt from BaseModel.
@EnableJpaAuditing
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
