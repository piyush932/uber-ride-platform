package com.example.bookingservice.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Used with SPRING_PROFILES_ACTIVE=kafka (Docker stage). */
@Component
@Profile("kafka")
public class KafkaBookingEventPublisher implements BookingEventPublisher {

    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;
    private final String topic;

    public KafkaBookingEventPublisher(KafkaTemplate<String, BookingEvent> kafkaTemplate,
                                      @Value("${booking.events.topic:booking-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(BookingEvent event) {
        // key by booking id so events for one booking stay in order in one partition
        kafkaTemplate.send(topic, String.valueOf(event.bookingId()), event);
    }
}
