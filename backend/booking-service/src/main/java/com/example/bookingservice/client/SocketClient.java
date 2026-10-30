package com.example.bookingservice.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Pushes a ride request to a driver through socket-service, protected by a circuit breaker. */
@Component
public class SocketClient {

    private static final Logger log = LoggerFactory.getLogger(SocketClient.class);

    private final RestClient restClient;

    public SocketClient(@Value("${socket.service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "socketCB", fallbackMethod = "notifyFallback")
    public void notifyDriver(long driverId, long bookingId, long passengerId,
                             double pickupLatitude, double pickupLongitude) {
        restClient.post()
            .uri("/api/v1/socket/rides/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "driverId", driverId,
                "bookingId", bookingId,
                "passengerId", passengerId,
                "pickupLatitude", pickupLatitude,
                "pickupLongitude", pickupLongitude))
            .retrieve()
            .toBodilessEntity();
    }

    /** The booking stays PENDING; the passenger can retry or another search can run. */
    private void notifyFallback(long driverId, long bookingId, long passengerId,
                                double pickupLatitude, double pickupLongitude, Throwable cause) {
        log.warn("Could not push ride {} to driver {}: {}", bookingId, driverId, cause.getMessage());
    }
}
