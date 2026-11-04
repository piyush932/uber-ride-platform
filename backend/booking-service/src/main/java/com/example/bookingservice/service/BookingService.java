package com.example.bookingservice.service;

import com.example.bookingservice.dto.BookingDtos.AcceptBookingRequest;
import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.model.IdempotencyRecord;
import com.example.bookingservice.model.RideOffer;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.IdempotencyRepository;
import com.example.entityservice.models.Booking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BookingService {

    private final BookingRepository repository;
    private final IdempotencyRepository idempotencyRepository;
    private final OfferService offerService;
    private final BookingEventPublisher events;

    public BookingService(BookingRepository repository,
                          IdempotencyRepository idempotencyRepository,
                          OfferService offerService,
                          BookingEventPublisher events) {
        this.repository = repository;
        this.idempotencyRepository = idempotencyRepository;
        this.offerService = offerService;
        this.events = events;
    }

    /**
     * With an Idempotency-Key header, a retried request returns the same booking
     * (HTTP 200) instead of creating a second one. The booking is offered to the nearest
     * eligible driver for a limited time. noRollbackFor keeps a CANCELLED booking on the 409/503 paths.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public BookingResponse create(CreateBookingRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing =
                idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Booking booking = repository.findById(existing.get().getBookingId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));
                return BookingResponse.from(booking, booking.getDriverId());
            }
        }

        Booking booking = Booking.builder()
            .status(Booking.BookingStatus.PENDING)
            .passengerId(request.passengerId())
            .pickupLatitude(request.pickupLatitude())
            .pickupLongitude(request.pickupLongitude())
            .dropLatitude(request.dropLatitude())
            .dropLongitude(request.dropLongitude())
            .build();
        booking = repository.save(booking);

        Optional<RideOffer> offer;
        try {
            offer = offerService.offerNext(booking);
        } catch (ResponseStatusException e) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancelReason("BOOKING_FAILED");
            repository.save(booking);
            throw e;
        }

        if (offer.isEmpty()) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancelReason("NO_DRIVERS_NEARBY");
            booking = repository.saveAndFlush(booking);
            events.publish(BookingEvent.of(booking.getId(), "BOOKING_CANCELLED",
                booking.getPassengerId(), null, booking.getCancelReason()));
            throw new ResponseStatusException(CONFLICT, "No drivers nearby");
        }

        saveIdempotencyKey(idempotencyKey, booking.getId());
        return BookingResponse.from(booking, offer.get().getDriverId());
    }

    /**
     * Legacy accept, used by socket-service. The driver must hold a live offer for this booking,
     * otherwise it would bypass the offer rules.
     */
    @Transactional
    public BookingResponse accept(Long bookingId, AcceptBookingRequest request) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", not waiting for a driver");
        }

        RideOffer offer = offerService.findLiveOffer(bookingId, request.driverId())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT,
                "This driver has no live offer for this booking"));

        Booking confirmed = offerService.confirm(offer, booking);
        return BookingResponse.from(confirmed, confirmed.getDriverId());
    }

    /** Legacy complete. The driver flow uses /api/v1/driver/rides/{id}/complete after start. */
    @Transactional
    public BookingResponse complete(Long bookingId) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED
            && booking.getStatus() != Booking.BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", only a CONFIRMED or IN_PROGRESS booking can be completed");
        }

        booking.setStatus(Booking.BookingStatus.COMPLETED);
        booking = repository.save(booking);
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_COMPLETED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    /** Allowed for PENDING and CONFIRMED only. Open offers are withdrawn. */
    @Transactional
    public BookingResponse cancel(Long bookingId, String reason) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() == Booking.BookingStatus.COMPLETED
            || booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new ResponseStatusException(CONFLICT, "Booking is already " + booking.getStatus());
        }
        if (booking.getStatus() == Booking.BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "A ride in progress cannot be cancelled");
        }

        offerService.withdrawOpen(bookingId);
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancelReason(reason == null || reason.isBlank() ? "CANCELLED_BY_PASSENGER" : reason);
        booking = repository.save(booking);
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_CANCELLED",
            booking.getPassengerId(), booking.getDriverId(), booking.getCancelReason()));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    public BookingResponse get(Long bookingId) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    public List<BookingResponse> byPassenger(Long passengerId) {
        return repository.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
            .map(b -> BookingResponse.from(b, b.getDriverId()))
            .toList();
    }

    private void saveIdempotencyKey(String key, Long bookingId) {
        if (key == null || key.isBlank()) {
            return;
        }
        idempotencyRepository.save(IdempotencyRecord.builder()
            .idempotencyKey(key)
            .bookingId(bookingId)
            .build());
    }
}
