package com.example.socketservice.service;

import com.example.socketservice.dto.SocketDtos.BookingUpdate;
import com.example.socketservice.dto.SocketDtos.NotifyDriverRequest;
import com.example.socketservice.dto.SocketDtos.RideRequestMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocketServiceTest {

    @Mock
    private SimpMessagingTemplate template;

    private SocketService service;

    @BeforeEach
    void setUp() {
        service = new SocketService(template);
    }

    @Test
    void notifyDriverSendsTheRideToThatDriversTopic() {
        service.notifyDriver(new NotifyDriverRequest(7L, 101L, 1L, 12.9716, 77.5946));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/drivers/7/rides"), payload.capture());
        RideRequestMessage sent = (RideRequestMessage) payload.getValue();
        assertEquals(101L, sent.bookingId());
        assertEquals(1L, sent.passengerId());
        assertEquals(12.9716, sent.pickupLatitude());
        assertEquals(77.5946, sent.pickupLongitude());
    }

    @Test
    void bookingUpdatesGoToTheBookingsTopic() {
        BookingUpdate update = new BookingUpdate(101L, "CONFIRMED", 7L, "ok");

        service.publishBookingUpdate(update);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/bookings/101"), payload.capture());
        assertEquals(update, payload.getValue());
    }
}
