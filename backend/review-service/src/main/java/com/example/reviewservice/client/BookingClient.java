package com.example.reviewservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Asks booking-service whether a booking exists, is completed, and who took part. */
@Component
public class BookingClient {

    public record BookingView(Long id, String status, Long passengerId, Long driverId) {
    }

    private final RestClient restClient;

    public BookingClient(@Value("${booking.service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public BookingView getBooking(long bookingId) {
        try {
            return restClient.get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .retrieve()
                .body(BookingView.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(NOT_FOUND, "Booking not found");
        } catch (RestClientException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Booking service is not reachable");
        }
    }
}
