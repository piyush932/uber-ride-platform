package com.example.bookingservice.service;

import com.example.bookingservice.dto.BookingDtos.AcceptBookingRequest;
import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.model.IdempotencyRecord;
import com.example.bookingservice.model.RideOffer;
import com.example.bookingservice.model.RideOffer.OfferStatus;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.IdempotencyRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock
    private BookingRepository repository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private OfferService offerService;

    @Mock
    private BookingEventPublisher events;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, idempotencyRepository, offerService, events);
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

    private RideOffer offerFor(long driverId) {
        return RideOffer.builder()
            .id(5L)
            .bookingId(1L)
            .driverId(driverId)
            .status(OfferStatus.OFFERED)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(20))
            .build();
    }

    private void saveAssignsId() {
        when(repository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(1L);
            }
            return b;
        });
    }

    @Test
    void createOffersTheRideToTheNearestEligibleDriver() {
        saveAssignsId();
        when(offerService.offerNext(any(Booking.class))).thenReturn(Optional.of(offerFor(9001L)));

        BookingResponse response = service.create(createRequest(), null);

        assertEquals(Booking.BookingStatus.PENDING.name(), response.status());
        assertEquals(9001L, response.candidateDriverId());
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
        verify(offerService, never()).offerNext(any());
    }

    @Test
    void createWithNoEligibleDriverCancelsTheBooking() {
        saveAssignsId();
        when(repository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offerService.offerNext(any(Booking.class))).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(createRequest(), "key-1"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(idempotencyRepository, never()).save(any());

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals("BOOKING_CANCELLED", event.getValue().type());
        assertEquals("NO_DRIVERS_NEARBY", event.getValue().reason());
    }

    @Test
    void createWhenLocationServiceIsDownCancelsAndRethrows() {
        saveAssignsId();
        when(offerService.offerNext(any(Booking.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location service is not reachable"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(createRequest(), null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(Booking.BookingStatus.CANCELLED, saved.getValue().getStatus());
        assertEquals("BOOKING_FAILED", saved.getValue().getCancelReason());
    }

    @Test
    void acceptWithoutALiveOfferIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.PENDING)));
        when(offerService.findLiveOffer(1L, 9001L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.accept(1L, new AcceptBookingRequest(9001L)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(offerService, never()).confirm(any(), any());
    }

    @Test
    void acceptWithALiveOfferConfirmsTheBooking() {
        Booking pending = savedBooking(Booking.BookingStatus.PENDING);
        RideOffer offer = offerFor(9001L);
        Booking confirmed = savedBooking(Booking.BookingStatus.CONFIRMED);
        confirmed.setDriverId(9001L);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));
        when(offerService.findLiveOffer(1L, 9001L)).thenReturn(Optional.of(offer));
        when(offerService.confirm(offer, pending)).thenReturn(confirmed);

        BookingResponse response = service.accept(1L, new AcceptBookingRequest(9001L));

        assertEquals(Booking.BookingStatus.CONFIRMED.name(), response.status());
        assertEquals(9001L, response.driverId());
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
    void completeMarksAnInProgressBookingCompleted() {
        Booking inProgress = savedBooking(Booking.BookingStatus.IN_PROGRESS);
        inProgress.setDriverId(9001L);
        when(repository.findById(1L)).thenReturn(Optional.of(inProgress));
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
    void cancelMarksAPendingBookingCancelledAndWithdrawsOffers() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.PENDING)));
        when(repository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.cancel(1L, "changed my mind");

        assertEquals(Booking.BookingStatus.CANCELLED.name(), response.status());
        assertEquals("changed my mind", response.cancelReason());
        verify(offerService).withdrawOpen(1L);
    }

    @Test
    void cancelOnAnInProgressRideIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.IN_PROGRESS)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.cancel(1L, null));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(offerService, never()).withdrawOpen(any());
    }

    @Test
    void getReturnsTheBooking() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedBooking(Booking.BookingStatus.PENDING)));

        assertEquals(1L, service.get(1L).id());
    }
}
