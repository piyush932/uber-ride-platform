package com.example.bookingservice.service;

import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.DriverDtos.DashboardResponse;
import com.example.bookingservice.dto.DriverDtos.OfferView;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.model.RideOffer;
import com.example.bookingservice.model.RideOffer.OfferStatus;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.RideOfferRepository;
import com.example.entityservice.models.Booking;
import com.example.entityservice.models.Booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Everything a driver does: see offers, accept or reject, start and complete a trip. Also expiry and reassignment. */
@Service
public class DriverRideService {

    private static final Logger log = LoggerFactory.getLogger(DriverRideService.class);
    private static final List<BookingStatus> ACTIVE_RIDE = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    private static final long ORPHAN_WINDOW_MINUTES = 15;

    private final RideOfferRepository offers;
    private final BookingRepository bookings;
    private final OfferService offerService;
    private final BookingEventPublisher events;

    public DriverRideService(RideOfferRepository offers,
                             BookingRepository bookings,
                             OfferService offerService,
                             BookingEventPublisher events) {
        this.offers = offers;
        this.bookings = bookings;
        this.offerService = offerService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(long driverId) {
        Instant now = Instant.now();
        BookingResponse active = bookings
            .findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, ACTIVE_RIDE)
            .map(b -> BookingResponse.from(b, b.getDriverId()))
            .orElse(null);

        List<OfferView> views = new ArrayList<>();
        for (RideOffer offer : offers.findByDriverIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            driverId, OfferStatus.OFFERED, now)) {
            bookings.findById(offer.getBookingId()).ifPresent(b -> views.add(new OfferView(
                offer.getId(), b.getId(),
                b.getPickupLatitude(), b.getPickupLongitude(),
                b.getDropLatitude(), b.getDropLongitude(),
                offer.getExpiresAt(), secondsLeft(now, offer.getExpiresAt()))));
        }
        return new DashboardResponse(active, views);
    }

    @Transactional
    public BookingResponse acceptOffer(long driverId, long offerId) {
        RideOffer offer = ownedOffer(driverId, offerId);
        Booking booking = bookings.findById(offer.getBookingId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));
        Booking confirmed = offerService.confirm(offer, booking);
        return BookingResponse.from(confirmed, confirmed.getDriverId());
    }

    @Transactional
    public BookingResponse rejectOffer(long driverId, long offerId) {
        RideOffer offer = ownedOffer(driverId, offerId);
        if (offer.getStatus() != OfferStatus.OFFERED) {
            throw new ResponseStatusException(CONFLICT, "This offer is no longer open");
        }
        Booking booking = bookings.findById(offer.getBookingId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        offer.setStatus(OfferStatus.REJECTED);
        try {
            offers.saveAndFlush(offer);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(CONFLICT, "This offer just changed. Refresh and try again.");
        }
        events.publish(BookingEvent.of(booking.getId(), "OFFER_REJECTED",
            booking.getPassengerId(), driverId, null));

        reassignQuietly(booking);
        return BookingResponse.from(booking, booking.getDriverId());
    }

    @Transactional
    public BookingResponse start(long driverId, long bookingId) {
        Booking booking = ownedBooking(driverId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", only a CONFIRMED ride can be started");
        }
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = saveBooking(booking);
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_STARTED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    @Transactional
    public BookingResponse complete(long driverId, long bookingId) {
        Booking booking = ownedBooking(driverId, bookingId);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", only an IN_PROGRESS ride can be completed");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        booking = saveBooking(booking);
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_COMPLETED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> history(long driverId) {
        return bookings.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
            .map(b -> BookingResponse.from(b, b.getDriverId()))
            .toList();
    }

    // ---- used by OfferExpiryJob, each call in its own transaction ----

    public List<Long> expiredOfferIds() {
        return offers.findByStatusAndExpiresAtBefore(OfferStatus.OFFERED, Instant.now()).stream()
            .map(RideOffer::getId)
            .toList();
    }

    @Transactional
    public void expireOffer(Long offerId) {
        RideOffer offer = offers.findById(offerId).orElse(null);
        if (offer == null || offer.getStatus() != OfferStatus.OFFERED
            || offer.getExpiresAt().isAfter(Instant.now())) {
            return;
        }
        offer.setStatus(OfferStatus.EXPIRED);
        offers.saveAndFlush(offer);

        Booking booking = bookings.findById(offer.getBookingId()).orElse(null);
        if (booking != null) {
            events.publish(BookingEvent.of(booking.getId(), "OFFER_EXPIRED",
                booking.getPassengerId(), offer.getDriverId(), null));
            reassignQuietly(booking);
        }
    }

    /** Recent PENDING bookings with no live offer, for example after location-service was briefly down. */
    public List<Long> orphanBookingIds() {
        return bookings.findByStatusAndCreatedAtAfter(
                BookingStatus.PENDING, LocalDateTime.now().minusMinutes(ORPHAN_WINDOW_MINUTES)).stream()
            .filter(b -> !offers.existsByBookingIdAndStatus(b.getId(), OfferStatus.OFFERED))
            .map(Booking::getId)
            .toList();
    }

    @Transactional
    public void reassignBooking(Long bookingId) {
        Booking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING
            || offers.existsByBookingIdAndStatus(bookingId, OfferStatus.OFFERED)) {
            return;
        }
        offerService.reassign(booking);
    }

    // ---- helpers ----

    private void reassignQuietly(Booking booking) {
        try {
            offerService.reassign(booking);
        } catch (ResponseStatusException e) {
            log.warn("Could not reassign booking {} now ({}). The sweeper will retry.",
                booking.getId(), e.getReason());
        }
    }

    private RideOffer ownedOffer(long driverId, long offerId) {
        RideOffer offer = offers.findById(offerId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Offer not found"));
        if (!Objects.equals(offer.getDriverId(), driverId)) {
            throw new ResponseStatusException(NOT_FOUND, "Offer not found");
        }
        return offer;
    }

    private Booking ownedBooking(long driverId, long bookingId) {
        Booking booking = bookings.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));
        if (!Objects.equals(booking.getDriverId(), driverId)) {
            throw new ResponseStatusException(FORBIDDEN, "This ride belongs to another driver");
        }
        return booking;
    }

    private Booking saveBooking(Booking booking) {
        try {
            return bookings.saveAndFlush(booking);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(CONFLICT, "This ride just changed. Refresh and try again.");
        }
    }

    private static long secondsLeft(Instant now, Instant expiresAt) {
        return Math.max(0, (long) Math.ceil(Duration.between(now, expiresAt).toMillis() / 1000.0));
    }
}
