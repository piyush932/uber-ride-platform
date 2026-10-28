package com.example.socketservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class HttpBookingClient implements BookingClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBookingClient.class);

    private final RestClient restClient;

    public HttpBookingClient(@Value("${booking.service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    @Override
    public boolean accept(long bookingId, long driverId) {
        try {
            restClient.post()
                .uri("/api/v1/bookings/{id}/accept", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("driverId", driverId))
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Booking service did not confirm booking {}: {}", bookingId, e.getMessage());
            return false;
        }
    }
}
