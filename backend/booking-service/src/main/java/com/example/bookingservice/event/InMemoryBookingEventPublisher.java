package com.example.bookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Default for local runs: writes the event to the console instead of Kafka. */
@Component
@Profile("!kafka")
public class InMemoryBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBookingEventPublisher.class);

    @Override
    public void publish(BookingEvent event) {
        log.info("BOOKING EVENT {} booking={} passenger={} driver={} reason={}",
            event.type(), event.bookingId(), event.passengerId(), event.driverId(), event.reason());
    }
}
