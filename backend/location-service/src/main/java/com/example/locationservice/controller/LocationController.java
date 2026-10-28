package com.example.locationservice.controller;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;
import com.example.locationservice.dto.LocationDtos.UpdateLocationRequest;
import com.example.locationservice.store.LocationStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private static final int MAX_LIMIT = 50;
    private static final double MAX_RADIUS_KM = 100.0;

    private final LocationStore store;

    public LocationController(LocationStore store) {
        this.store = store;
    }

    /** A driver app calls this every few seconds with its current position. */
    @PutMapping("/drivers/{driverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable("driverId") String driverId, @Valid @RequestBody UpdateLocationRequest request) {
        store.save(driverId, request.latitude(), request.longitude());
    }

    /** The driver goes offline. */
    @DeleteMapping("/drivers/{driverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("driverId") String driverId) {
        store.remove(driverId);
    }

    @GetMapping("/nearby")
    public List<NearbyDriver> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(name = "radiusKm", defaultValue = "5") double radiusKm,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {

        if (lat < -90 || lat > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lng must be between -180 and 180");
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be between 0 and " + MAX_RADIUS_KM);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        return store.findNearby(lat, lng, radiusKm, limit);
    }
}
