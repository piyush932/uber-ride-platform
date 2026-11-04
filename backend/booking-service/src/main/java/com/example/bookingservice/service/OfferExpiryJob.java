package com.example.bookingservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every couple of seconds: expire stale offers, then rescue PENDING bookings that have no live offer. */
@Component
public class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final DriverRideService service;

    public OfferExpiryJob(DriverRideService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${booking.offer.sweep-ms:2000}")
    public void sweep() {
        try {
            for (Long offerId : service.expiredOfferIds()) {
                try {
                    service.expireOffer(offerId);
                } catch (RuntimeException e) {
                    log.warn("Could not expire offer {}: {}", offerId, e.toString());
                }
            }
            for (Long bookingId : service.orphanBookingIds()) {
                try {
                    service.reassignBooking(bookingId);
                } catch (RuntimeException e) {
                    log.warn("Could not reassign booking {}: {}", bookingId, e.toString());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Offer sweep failed: {}", e.toString());
        }
    }
}
