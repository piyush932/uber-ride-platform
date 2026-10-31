package com.example.bookingservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
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
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public SocketClient(@Value("${socket.service.url}") String baseUrl,
                        CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * If socket-service is down or slow, the call fails fast (after a few failures the circuit opens)
     * and the booking stays PENDING instead of failing the whole request.
     */
    public void notifyDriver(long driverId, long bookingId, long passengerId,
                             double pickupLatitude, double pickupLongitude) {
        circuitBreakerFactory.create("socketCB").run(() -> {
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
            return Boolean.TRUE;
        }, throwable -> {
            log.warn("Could not push ride {} to driver {}: {}", bookingId, driverId, throwable.toString());
            return Boolean.FALSE;
        });
    }
}
