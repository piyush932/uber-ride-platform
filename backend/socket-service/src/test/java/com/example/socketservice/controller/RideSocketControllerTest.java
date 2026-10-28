package com.example.socketservice.controller;

import com.example.socketservice.dto.SocketDtos.AcceptRequest;
import com.example.socketservice.dto.SocketDtos.BookingUpdate;
import com.example.socketservice.service.BookingClient;
import com.example.socketservice.service.SocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideSocketControllerTest {

    @Mock
    private BookingClient bookingClient;

    @Mock
    private SocketService socketService;

    private RideSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new RideSocketController(bookingClient, socketService);
    }

    @Test
    void confirmedAcceptIsPublishedAsConfirmed() {
        when(bookingClient.accept(101L, 7L)).thenReturn(true);

        controller.accept(new AcceptRequest(101L, 7L));

        ArgumentCaptor<BookingUpdate> update = ArgumentCaptor.forClass(BookingUpdate.class);
        verify(socketService).publishBookingUpdate(update.capture());
        assertEquals("CONFIRMED", update.getValue().status());
        assertEquals(101L, update.getValue().bookingId());
        assertEquals(7L, update.getValue().driverId());
    }

    @Test
    void rejectedAcceptIsPublishedAsFailed() {
        when(bookingClient.accept(101L, 7L)).thenReturn(false);

        controller.accept(new AcceptRequest(101L, 7L));

        ArgumentCaptor<BookingUpdate> update = ArgumentCaptor.forClass(BookingUpdate.class);
        verify(socketService).publishBookingUpdate(update.capture());
        assertEquals("ACCEPT_FAILED", update.getValue().status());
    }

    @Test
    void messageWithoutIdsIsIgnored() {
        controller.accept(new AcceptRequest(null, 7L));

        verifyNoInteractions(bookingClient, socketService);
    }
}
