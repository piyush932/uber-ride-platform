package com.example.bookingservice.event;

/** Where booking events go. In-memory locally, Kafka in the Docker stage. */
public interface BookingEventPublisher {

    void publish(BookingEvent event);
}
