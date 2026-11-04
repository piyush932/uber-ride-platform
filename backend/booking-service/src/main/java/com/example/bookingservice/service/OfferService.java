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
import com.example.entityservice.models.Booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * Matching rules. Every method here runs inside the caller's transaction, so a failure
 * in the caller rolls the whole step back. Methods are deliberately not @Transactional
 * themselves: an exception from location-service must stay catchable by the caller
 * without marking the surrounding transaction rollback-only.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final List<BookingStatus> ACTIVE_RIDE = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final RideOfferRepository offers;
    private final BookingRepository bookings;
    private final LocationClient locationClient;
    private final SocketClient socketClient;
    private final BookingEventPublisher events;
    private final long ttlSeconds;
    private final int maxAttempts;

    public OfferService(RideOfferRepository offers,
                        BookingRepository bookings,
                        LocationClient locationClient,
                        SocketClient socketClient,
                        BookingEventPublisher events,
                        @Value("${booking.offer.ttl-seconds:20}") long ttlSeconds,
                        @Value("${booking.offer.max-attempts:5}") int maxAttempts) {
        this.offers = offers;
        this.bookings = bookings;
        this.locationClient = locationClient;
        this.socketClient = socketClient;
        this.events = events;
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
    }

    /** Offers the ride to the nearest driver who is not busy and has not been tried for this booking. */
    public Optional<RideOffer> offerNext(Booking booking) {
        List<RideOffer> prior = offers.findByBookingId(booking.getId());
        if (prior.size() >= maxAttempts) {
            return Optional.empty();
        }
        Set<Long> tried = prior.stream().map(RideOffer::getDriverId).collect(Collectors.toSet());

        List<NearbyDriver> candidates = locationClient.findCandidates(
            booking.getPickupLatitude(), booking.getPickupLongitude());
        if (candidates == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        for (NearbyDriver candidate : candidates) {
            Long driverId = toDriverId(candidate.driverId());
            if (driverId == null || tried.contains(driverId) || isBusy(driverId, now)) {
                continue;
            }
            RideOffer offer = offers.save(RideOffer.builder()
                .bookingId(booking.getId())
                .driverId(driverId)
                .status(OfferStatus.OFFERED)
                .createdAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build());
            pushToDriver(booking, driverId);
            events.publish(BookingEvent.of(booking.getId(), "DRIVER_NOTIFIED",
                booking.getPassengerId(), driverId, null));
            return Optional.of(offer);
        }
        return Optional.empty();
    }

    /** A driver is busy with a live offer, or a CONFIRMED / IN_PROGRESS ride. */
    public boolean isBusy(Long driverId, Instant now) {
        return offers.existsByDriverIdAndStatusAndExpiresAtAfter(driverId, OfferStatus.OFFERED, now)
            || bookings.existsByDriverIdAndStatusIn(driverId, ACTIVE_RIDE);
    }

    public Optional<RideOffer> findLiveOffer(Long bookingId, Long driverId) {
        return offers.findFirstByBookingIdAndDriverIdAndStatusAndExpiresAtAfter(
            bookingId, driverId, OfferStatus.OFFERED, Instant.now());
    }

    /** Accepts a live offer and confirms the booking. Used by the driver endpoint and the legacy accept. */
    public Booking confirm(RideOffer offer, Booking booking) {
        if (offer.getStatus() != OfferStatus.OFFERED) {
            throw new ResponseStatusException(CONFLICT, "This offer is no longer open");
        }
        if (!offer.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(CONFLICT, "This offer has expired");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", not waiting for a driver");
        }

        offer.setStatus(OfferStatus.ACCEPTED);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setDriverId(offer.getDriverId());
        try {
            offers.saveAndFlush(offer);
            booking = bookings.saveAndFlush(booking);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(CONFLICT, "This ride was already taken or changed");
        }
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_CONFIRMED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return booking;
    }

    /** Closes any open offer for a booking, for example when the passenger cancels. */
    public void withdrawOpen(Long bookingId) {
        List<RideOffer> open = offers.findByBookingIdAndStatus(bookingId, OfferStatus.OFFERED);
        for (RideOffer offer : open) {
            offer.setStatus(OfferStatus.WITHDRAWN);
        }
        offers.saveAll(open);
    }

    /** Moves a PENDING booking on to the next driver, or cancels it when nobody is left. */
    public void reassign(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            return;
        }
        if (offerNext(booking).isEmpty()) {
            cancelNoDriver(booking, "NO_DRIVER_AVAILABLE");
        }
    }

    public void cancelNoDriver(Booking booking, String reason) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(reason);
        Booking saved = bookings.saveAndFlush(booking);
        events.publish(BookingEvent.of(saved.getId(), "BOOKING_CANCELLED",
            saved.getPassengerId(), null, reason));
    }

    private void pushToDriver(Booking booking, long driverId) {
        try {
            socketClient.notifyDriver(driverId, booking.getId(), booking.getPassengerId(),
                booking.getPickupLatitude(), booking.getPickupLongitude());
        } catch (RuntimeException e) {
            // The driver dashboard polls, so a missed push only costs a few seconds.
            log.warn("Push to driver {} for booking {} failed: {}", driverId, booking.getId(), e.toString());
        }
    }

    private static Long toDriverId(String driverId) {
        try {
            return Long.parseLong(driverId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
