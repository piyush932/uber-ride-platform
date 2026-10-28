package com.example.socketservice.controller;

import com.example.socketservice.service.SocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SocketRestControllerTest {

    @Mock
    private SocketService socketService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SocketRestController(socketService)).build();
    }

    @Test
    void notifyReturns202AndPassesTheRequestOn() throws Exception {
        mvc.perform(post("/api/v1/socket/rides/notify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":7,\"bookingId\":101,\"passengerId\":1,\"pickupLatitude\":12.9716,\"pickupLongitude\":77.5946}"))
            .andExpect(status().isAccepted());

        verify(socketService).notifyDriver(any());
    }

    @Test
    void notifyWithoutDriverIdReturns400() throws Exception {
        mvc.perform(post("/api/v1/socket/rides/notify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":101,\"pickupLatitude\":12.9716,\"pickupLongitude\":77.5946}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(socketService);
    }
}
