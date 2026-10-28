package com.example.locationservice.controller;

import com.example.locationservice.dto.LocationDtos.NearbyDriver;
import com.example.locationservice.store.LocationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock
    private LocationStore store;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LocationController(store)).build();
    }

    @Test
    void updateReturns204AndSavesThePosition() throws Exception {
        mvc.perform(put("/api/v1/locations/drivers/d1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":12.97,\"longitude\":77.59}"))
            .andExpect(status().isNoContent());

        verify(store).save("d1", 12.97, 77.59);
    }

    @Test
    void updateWithLatitudeOutOfRangeReturns400() throws Exception {
        mvc.perform(put("/api/v1/locations/drivers/d1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":91,\"longitude\":77.59}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(store);
    }

    @Test
    void removeReturns204() throws Exception {
        mvc.perform(delete("/api/v1/locations/drivers/d1"))
            .andExpect(status().isNoContent());

        verify(store).remove("d1");
    }

    @Test
    void nearbyReturnsTheStoreResults() throws Exception {
        when(store.findNearby(12.97, 77.59, 5.0, 10))
            .thenReturn(List.of(new NearbyDriver("d1", 0.5, 12.971, 77.591)));

        mvc.perform(get("/api/v1/locations/nearby?lat=12.97&lng=77.59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].driverId").value("d1"))
            .andExpect(jsonPath("$[0].distanceKm").value(0.5));
    }

    @Test
    void nearbyWithLongitudeOutOfRangeReturns400() throws Exception {
        mvc.perform(get("/api/v1/locations/nearby?lat=12.97&lng=200"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(store);
    }
}
