package com.example.locationservice.store;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;

import java.util.List;

/**
 * Where driver positions live. The in-memory version is the default for local runs,
 * the Redis version (profile "redis") is used in the Docker stage.
 */
public interface LocationStore {

    void save(String driverId, double latitude, double longitude);

    void remove(String driverId);

    /** Drivers within radiusKm of the point, nearest first, at most limit entries. */
    List<NearbyDriver> findNearby(double latitude, double longitude, double radiusKm, int limit);
}
