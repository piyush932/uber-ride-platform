package com.example.bookingservice.service;

import com.example.bookingservice.client.LocationClient;
import com.example.bookingservice.client.LocationClient.NearbyDriver;
import com.example.bookingservice.client.SocketClient;
import com.example.bookingservice.dto.BookingDtos.AcceptBookingRequest;
import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.model.IdempotencyRecord;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.IdempotencyRepository;
import com.example.entityservice.models.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository repository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private LocationClient locationClient;

    @Mock
    private SocketClient socketClient;

    @Mock
    private BookingEventPublisher events;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, idempotencyRepository, locationClient, socketClient, events, 5.0);
    }

    private CreateBookingRequest createRequest() {
        return new CreateBookingRequest(1L, 12.9716, 77.5946, 12.9352, 77.6245);
    }

    private Booking savedBooking(Booking.BookingStatus status) {
        Booking booking = Booking.builder()
            .status(status)
            .passengerId(1L)
            .pickupLatitude(12.9716)
            .pickupLongitude(77.5946)
            .build();
        booking.setId(1L);
        return booking;
    }

    @Test
    void createAssignsTheNearestDriverAndNotifiesThem() {
        when(repository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(1L);
            }
            return b;
        });
        when(locationClient.findNearby(12.9716, 77.5946))
            .thenReturn(List.of(new NearbyDriver("9001", 0.5, 12.972, 77.595)));

        BookingResponse response = service.create(createRequest(), null);

        assertEquals(Booking.BookingStatus.PENDING.name(), response.status());
        assertEquals(9001L, response.candidateDriverId());
        verify(socketClient).notifyDriver(9001L, 1L, 1L, 12.9716, 77.5946);

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("DRIVER_NOTIFIED", event.getValue().type());
        assertEquals(9001L, event.getValue().driverId());
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameBooking() {
        Booking existing = savedBooking(Booking.BookingStatus.PENDING);
        when(idempotencyRepository.findByIdempotencyKey("abc-123"))
            .thenReturn(Optional.of(IdempotencyRecord.builder()
                .idempotencyKey("abc-123")
                .bookingId(1L)
                .build()));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        BookingResponse response = service.create(createRequest(), "abc-123");

        assertEquals(1L, response.id());
        verify(locationClient, never()).findNearby(anyDouble(), anyDouble());
    }

    @Test
    void createWithNoNearbyDriversCancelsTheBooking() {
        when(repository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(1L);
            }
            return b;
        });
        when(locationClient.findNearby(12.9716, 77.5946)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(createRequest(), null));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(socketClient, never()).notifyDriver(anyLong(), anyLong(), anyLong(), anyDouble(), anyDouble());

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_CANCELLED", event.getValue().type());
        assertEquals("NO_DRIVERS_NEARBY", event.getValue().reason());
    }

    @Test
    void acceptConfirmsAPendingBooking() {
        Booking pending = savedBooking(Booking.BookingStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));
        when(repository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.accept(1L, new AcceptBookingRequest(9001L));

        assertEquals(Booking.BookingStatus.CONFIRMED.name(), response.status());
        assertEquals(9001L, response.driverId());

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_CONFIRMED", event.getValue().type());
    }

    @Test
    void acceptOnAConfirmedBookingIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.CONFIRMED)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.accept(1L, new AcceptBookingRequest(9002L)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(events, never()).publish(any());
    }

    @Test
    void completeMarksAConfirmedBookingCompleted() {
        Booking confirmed = savedBooking(Booking.BookingStatus.CONFIRMED);
        confirmed.setDriverId(9001L);
        when(repository.findById(1L)).thenReturn(Optional.of(confirmed));
        when(repository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.complete(1L);

        assertEquals(Booking.BookingStatus.COMPLETED.name(), response.status());

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_COMPLETED", event.getValue().type());
    }

    @Test
    void completeOnAPendingBookingIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.PENDING)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.complete(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(events, never()).publish(any());
    }

    @Test
    void cancelMarksAPendingBookingCancelled() {
        Booking pending = savedBooking(Booking.BookingStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));
        when(repository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.cancel(1L, "changed my mind");

        assertEquals(Booking.BookingStatus.CANCELLED.name(), response.status());
        assertEquals("changed my mind", response.cancelReason());
    }

    @Test
    void getReturnsTheBooking() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.PENDING)));

        assertEquals(1L, service.get(1L).id());
    }
}
