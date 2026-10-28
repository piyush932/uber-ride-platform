package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BookingController(service)).build();
    }

    private BookingResponse sample() {
        return new BookingResponse(1L, "PENDING", 1L, null, 9001L,
            12.9716, 77.5946, null, null, null, null);
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any(CreateBookingRequest.class))).thenReturn(sample());

        mvc.perform(post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passengerId\":1,\"pickupLatitude\":12.9716,\"pickupLongitude\":77.5946}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.candidateDriverId").value(9001));
    }

    @Test
    void createWithoutPassengerIdReturns400() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pickupLatitude\":12.9716,\"pickupLongitude\":77.5946}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void acceptReturns200() throws Exception {
        when(service.accept(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/v1/bookings/1/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":9001}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unknownBookingReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        mvc.perform(get("/api/v1/bookings/99"))
            .andExpect(status().isNotFound());
    }
}
