package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.DriverDtos.DashboardResponse;
import com.example.bookingservice.dto.DriverDtos.OfferView;
import com.example.bookingservice.service.DriverRideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DriverRideControllerTest {

    @Mock
    private DriverRideService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DriverRideController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    private BookingResponse ride(String status) {
        return new BookingResponse(1L, status, 1L, 9001L, 9001L,
            12.9716, 77.5946, 12.9352, 77.6245, null, null);
    }

    @Test
    void dashboardReturnsTheOffersOfTheDriverInTheGatewayHeaders() throws Exception {
        when(service.dashboard(9001L)).thenReturn(new DashboardResponse(null, List.of(
            new OfferView(5L, 1L, 12.9716, 77.5946, 12.9352, 77.6245, null, 20L))));

        mvc.perform(get("/api/v1/driver/dashboard")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offers[0].id").value(5))
            .andExpect(jsonPath("$.offers[0].bookingId").value(1))
            .andExpect(jsonPath("$.offers[0].secondsRemaining").value(20));
    }

    @Test
    void aPassengerRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/driver/dashboard")
                .header("X-User-Id", "5")
                .header("X-User-Role", "PASSENGER"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void missingIdentityHeadersReturn400() throws Exception {
        mvc.perform(get("/api/v1/driver/dashboard"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void acceptUsesTheDriverIdFromTheHeaders() throws Exception {
        when(service.acceptOffer(9001L, 5L)).thenReturn(ride("CONFIRMED"));

        mvc.perform(post("/api/v1/driver/offers/5/accept")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectReturnsTheBookingStatus() throws Exception {
        when(service.rejectOffer(9001L, 5L)).thenReturn(ride("PENDING"));

        mvc.perform(post("/api/v1/driver/offers/5/reject")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void startMovesTheRideToInProgress() throws Exception {
        when(service.start(9001L, 1L)).thenReturn(ride("IN_PROGRESS"));

        mvc.perform(post("/api/v1/driver/rides/1/start")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void completeMovesTheRideToCompleted() throws Exception {
        when(service.complete(9001L, 1L)).thenReturn(ride("COMPLETED"));

        mvc.perform(post("/api/v1/driver/rides/1/complete")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void anotherDriversRideReturns403WithTheReason() throws Exception {
        when(service.start(9001L, 7L))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "This ride belongs to another driver"));

        mvc.perform(post("/api/v1/driver/rides/7/start")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("This ride belongs to another driver"));
    }

    @Test
    void historyListsTheDriversRides() throws Exception {
        when(service.history(9001L)).thenReturn(List.of(ride("COMPLETED")));

        mvc.perform(get("/api/v1/driver/rides")
                .header("X-User-Id", "9001")
                .header("X-User-Role", "DRIVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }
}
