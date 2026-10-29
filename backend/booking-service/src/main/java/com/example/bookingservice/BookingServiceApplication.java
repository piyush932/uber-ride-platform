package com.example.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
// The Booking entity lives in the shared entity-service library, outside this package,
// so it has to be scanned explicitly.
@EntityScan("com.example.entityservice.models")
// Fills createdAt / updatedAt from BaseModel.
@EnableJpaAuditing
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
