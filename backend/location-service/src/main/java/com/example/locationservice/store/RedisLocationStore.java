package com.example.locationservice.store;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis GEO implementation, used with SPRING_PROFILES_ACTIVE=redis.
 * GEO members have no expiry of their own, so a separate key with a TTL marks
 * which drivers are still active.
 */
@Component
@Profile("redis")
public class RedisLocationStore implements LocationStore {

    private static final String GEO_KEY = "drivers:geo";
    private static final String SEEN_PREFIX = "driver:seen:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisLocationStore(StringRedisTemplate redis, @Value("${location.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void save(String driverId, double latitude, double longitude) {
        // Redis expects longitude first
        redis.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), driverId);
        redis.opsForValue().set(SEEN_PREFIX + driverId, "1", ttl);
    }

    @Override
    public void remove(String driverId) {
        redis.opsForGeo().remove(GEO_KEY, driverId);
        redis.delete(SEEN_PREFIX + driverId);
    }

    @Override
    public List<NearbyDriver> findNearby(double latitude, double longitude, double radiusKm, int limit) {
        Circle area = new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS));
        // fetch extra, because stale drivers are filtered out afterwards
        GeoRadiusCommandArgs args = GeoRadiusCommandArgs.newGeoRadiusArgs()
            .includeDistance()
            .includeCoordinates()
            .sortAscending()
            .limit(limit * 3L);

        GeoResults<GeoLocation<String>> results = redis.opsForGeo().radius(GEO_KEY, area, args);
        List<NearbyDriver> found = new ArrayList<>();
        if (results == null) {
            return found;
        }

        for (GeoResult<GeoLocation<String>> result : results) {
            GeoLocation<String> location = result.getContent();
            String driverId = location.getName();
            if (!Boolean.TRUE.equals(redis.hasKey(SEEN_PREFIX + driverId))) {
                continue;
            }
            Point point = location.getPoint();
            double distance = Math.round(result.getDistance().getValue() * 1000.0) / 1000.0;
            found.add(new NearbyDriver(driverId, distance, point.getY(), point.getX()));
            if (found.size() == limit) {
                break;
            }
        }
        return found;
    }
}
