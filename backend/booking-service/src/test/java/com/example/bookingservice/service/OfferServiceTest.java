package com.example.bookingservice.service;

import com.example.bookingservice.client.LocationClient;
import com.example.bookingservice.client.LocationClient.NearbyDriver;
import com.example.bookingservice.client.SocketClient;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.model.RideOffer;
import com.example.bookingservice.model.RideOffer.OfferStatus;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.RideOfferRepository;
import com.example.entityservice.models.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferServiceTest {

    @Mock
    private RideOfferRepository offers;

    @Mock
    private BookingRepository bookings;

    @Mock
    private LocationClient locationClient;

    @Mock
    private SocketClient socketClient;

    @Mock
    private BookingEventPublisher events;

    private OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offers, bookings, locationClient, socketClient, events, 20L, 5);
        when(offers.save(any(RideOffer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offers.findByBookingId(1L)).thenReturn(List.of());
    }

    private Booking booking(Booking.BookingStatus status) {
        Booking booking = Booking.builder()
            .status(status)
            .passengerId(1L)
            .pickupLatitude(12.9716)
            .pickupLongitude(77.5946)
            .build();
        booking.setId(1L);
        return booking;
    }

    private RideOffer offer(long driverId, OfferStatus status, long expiresInSeconds) {
        return RideOffer.builder()
            .id(driverId)
            .bookingId(1L)
            .driverId(driverId)
            .status(status)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(expiresInSeconds))
            .build();
    }

    private NearbyDriver nearby(String id) {
        return new NearbyDriver(id, 0.5, 12.97, 77.59);
    }

    @Test
    void offerNextPicksTheNearestDriverWhoIsNotBusyOrAlreadyTried() {
        when(offers.findByBookingId(1L)).thenReturn(List.of(offer(9002L, OfferStatus.REJECTED, 20)));
        when(locationClient.findCandidates(12.9716, 77.5946))
            .thenReturn(List.of(nearby("9001"), nearby("9002"), nearby("9003")));
        when(bookings.existsByDriverIdAndStatusIn(eq(9001L), any())).thenReturn(true);

        Optional<RideOffer> result = service.offerNext(booking(Booking.BookingStatus.PENDING));

        assertTrue(result.isPresent());
        assertEquals(9003L, result.get().getDriverId());
        assertEquals(OfferStatus.OFFERED, result.get().getStatus());
        assertTrue(result.get().getExpiresAt().isAfter(Instant.now()));
        verify(socketClient).notifyDriver(9003L, 1L, 1L, 12.9716, 77.5946);

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("DRIVER_NOTIFIED", event.getValue().type());
        assertEquals(9003L, event.getValue().driverId());
    }

    @Test
    void offerNextSkipsADriverWithALiveOffer() {
        when(locationClient.findCandidates(12.9716, 77.5946)).thenReturn(List.of(nearby("9001")));
        when(offers.existsByDriverIdAndStatusAndExpiresAtAfter(eq(9001L), eq(OfferStatus.OFFERED), any(Instant.class)))
            .thenReturn(true);

        Optional<RideOffer> result = service.offerNext(booking(Booking.BookingStatus.PENDING));

        assertTrue(result.isEmpty());
        verify(offers, never()).save(any(RideOffer.class));
    }

    @Test
    void offerNextSkipsNonNumericDriverIds() {
        when(locationClient.findCandidates(12.9716, 77.5946)).thenReturn(List.of(nearby("abc")));

        assertTrue(service.offerNext(booking(Booking.BookingStatus.PENDING)).isEmpty());
    }

    @Test
    void offerNextStopsAtTheAttemptCap() {
        when(offers.findByBookingId(1L)).thenReturn(List.of(
            offer(1L, OfferStatus.REJECTED, 20), offer(2L, OfferStatus.EXPIRED, 20),
            offer(3L, OfferStatus.REJECTED, 20), offer(4L, OfferStatus.EXPIRED, 20),
            offer(5L, OfferStatus.REJECTED, 20)));

        assertTrue(service.offerNext(booking(Booking.BookingStatus.PENDING)).isEmpty());
        verify(locationClient, never()).findCandidates(anyDouble(), anyDouble());
    }

    @Test
    void reassignCancelsTheBookingWhenNobodyIsLeft() {
        when(locationClient.findCandidates(12.9716, 77.5946)).thenReturn(List.of());
        Booking pending = booking(Booking.BookingStatus.PENDING);

        service.reassign(pending);

        assertEquals(Booking.BookingStatus.CANCELLED, pending.getStatus());
        assertEquals("NO_DRIVER_AVAILABLE", pending.getCancelReason());
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_CANCELLED", event.getValue().type());
        assertEquals("NO_DRIVER_AVAILABLE", event.getValue().reason());
    }

    @Test
    void reassignIgnoresABookingThatIsNoLongerPending() {
        service.reassign(booking(Booking.BookingStatus.CONFIRMED));

        verify(locationClient, never()).findCandidates(anyDouble(), anyDouble());
        verify(events, never()).publish(any());
    }

    @Test
    void confirmAcceptsALiveOfferAndConfirmsTheBooking() {
        RideOffer live = offer(9001L, OfferStatus.OFFERED, 20);
        Booking pending = booking(Booking.BookingStatus.PENDING);

        Booking confirmed = service.confirm(live, pending);

        assertEquals(Booking.BookingStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(9001L, confirmed.getDriverId());
        assertEquals(OfferStatus.ACCEPTED, live.getStatus());
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_CONFIRMED", event.getValue().type());
    }

    @Test
    void confirmRejectsAnExpiredOffer() {
        RideOffer expired = offer(9001L, OfferStatus.OFFERED, -5);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.confirm(expired, booking(Booking.BookingStatus.PENDING)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(bookings, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void confirmRejectsAnOfferThatIsNoLongerOpen() {
        RideOffer rejected = offer(9001L, OfferStatus.REJECTED, 20);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.confirm(rejected, booking(Booking.BookingStatus.PENDING)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void withdrawOpenClosesEveryOpenOffer() {
        RideOffer open = offer(9001L, OfferStatus.OFFERED, 20);
        when(offers.findByBookingIdAndStatus(1L, OfferStatus.OFFERED)).thenReturn(List.of(open));

        service.withdrawOpen(1L);

        assertEquals(OfferStatus.WITHDRAWN, open.getStatus());
        verify(offers).saveAll(List.of(open));
    }
}
