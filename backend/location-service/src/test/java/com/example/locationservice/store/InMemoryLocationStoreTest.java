package com.example.locationservice.store;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLocationStoreTest {

    // search centre used by every test
    private static final double LAT = 12.9716;
    private static final double LNG = 77.5946;

    private MutableClock clock;
    private InMemoryLocationStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        store = new InMemoryLocationStore(clock, 30);
    }

    private void saveThreeDrivers() {
        store.save("far", 13.1986, 77.7066);   // about 28 km away
        store.save("near", 12.9720, 77.5950);  // about 0.06 km away
        store.save("mid", 12.9800, 77.6000);   // about 1.1 km away
    }

    @Test
    void returnsDriversInsideTheRadiusNearestFirst() {
        saveThreeDrivers();

        List<NearbyDriver> found = store.findNearby(LAT, LNG, 5, 10);

        assertEquals(List.of("near", "mid"), found.stream().map(NearbyDriver::driverId).toList());
        assertTrue(found.get(0).distanceKm() < 0.2);
    }

    @Test
    void aWiderRadiusIncludesTheFarDriver() {
        saveThreeDrivers();

        List<NearbyDriver> found = store.findNearby(LAT, LNG, 50, 10);

        assertEquals(List.of("near", "mid", "far"), found.stream().map(NearbyDriver::driverId).toList());
    }

    @Test
    void limitCapsTheNumberOfResults() {
        saveThreeDrivers();

        List<NearbyDriver> found = store.findNearby(LAT, LNG, 50, 1);

        assertEquals(1, found.size());
        assertEquals("near", found.get(0).driverId());
    }

    @Test
    void driversThatStopReportingAreDropped() {
        store.save("near", 12.9720, 77.5950);

        clock.advance(Duration.ofSeconds(31));

        assertTrue(store.findNearby(LAT, LNG, 5, 10).isEmpty());
    }

    @Test
    void aNewPositionReplacesTheOldOneAndRefreshesTheDriver() {
        store.save("d1", 13.1986, 77.7066);
        clock.advance(Duration.ofSeconds(20));
        store.save("d1", 12.9720, 77.5950);
        clock.advance(Duration.ofSeconds(20));

        List<NearbyDriver> found = store.findNearby(LAT, LNG, 1, 10);

        assertEquals(1, found.size());
        assertEquals("d1", found.get(0).driverId());
    }

    @Test
    void removedDriversDisappear() {
        saveThreeDrivers();

        store.remove("near");

        List<NearbyDriver> found = store.findNearby(LAT, LNG, 5, 10);
        assertEquals(List.of("mid"), found.stream().map(NearbyDriver::driverId).toList());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
