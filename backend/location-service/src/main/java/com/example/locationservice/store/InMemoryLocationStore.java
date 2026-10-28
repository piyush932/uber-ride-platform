package com.example.locationservice.store;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;
import com.example.locationservice.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryLocationStore implements LocationStore {

    private record Entry(double latitude, double longitude, Instant updatedAt) {
    }

    private final Map<String, Entry> drivers = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InMemoryLocationStore(Clock clock, @Value("${location.ttl-seconds:300}") long ttlSeconds) {
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void save(String driverId, double latitude, double longitude) {
        drivers.put(driverId, new Entry(latitude, longitude, clock.instant()));
    }

    @Override
    public void remove(String driverId) {
        drivers.remove(driverId);
    }

    @Override
    public List<NearbyDriver> findNearby(double latitude, double longitude, double radiusKm, int limit) {
        Instant cutoff = clock.instant().minus(ttl);
        return drivers.entrySet().stream()
            .filter(e -> e.getValue().updatedAt().isAfter(cutoff))
            .map(e -> new NearbyDriver(
                e.getKey(),
                round3(GeoUtils.distanceKm(latitude, longitude, e.getValue().latitude(), e.getValue().longitude())),
                e.getValue().latitude(),
                e.getValue().longitude()))
            .filter(d -> d.distanceKm() <= radiusKm)
            .sorted(Comparator.comparingDouble(NearbyDriver::distanceKm))
            .limit(limit)
            .toList();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
