package com.example.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Calls location-service for the nearest drivers. */
@Component
public class LocationClient {

    public record NearbyDriver(String driverId, double distanceKm, double latitude, double longitude) {
    }

    private final RestClient restClient;
    private final double radiusKm;

    public LocationClient(@Value("${location.service.url}") String baseUrl,
                          @Value("${booking.search.radius-km:5}") double radiusKm) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
        this.radiusKm = radiusKm;
    }

    /** Nearest driver first. */
    public List<NearbyDriver> findNearby(double latitude, double longitude) {
        try {
            return restClient.get()
                .uri("/api/v1/locations/nearby?lat={lat}&lng={lng}&radiusKm={radius}&limit=1",
                    latitude, longitude, radiusKm)
                .retrieve()
                .body(new ParameterizedTypeReference<List<NearbyDriver>>() {
                });
        } catch (RestClientException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Location service is not reachable");
        }
    }
}
