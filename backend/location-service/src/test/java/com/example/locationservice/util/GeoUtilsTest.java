package com.example.locationservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoUtilsTest {

    @Test
    void samePointIsZeroKm() {
        assertEquals(0.0, GeoUtils.distanceKm(12.9716, 77.5946, 12.9716, 77.5946), 1e-9);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111.195, GeoUtils.distanceKm(0, 0, 1, 0), 0.05);
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtils.distanceKm(12.9716, 77.5946, 13.1986, 77.7066);
        double ba = GeoUtils.distanceKm(13.1986, 77.7066, 12.9716, 77.5946);

        assertEquals(ab, ba, 1e-9);
    }
}
