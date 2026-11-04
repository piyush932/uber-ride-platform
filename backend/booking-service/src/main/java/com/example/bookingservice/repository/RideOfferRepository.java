package com.example.bookingservice.repository;

import com.example.bookingservice.model.RideOffer;
import com.example.bookingservice.model.RideOffer.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RideOfferRepository extends JpaRepository<RideOffer, Long> {

    List<RideOffer> findByBookingId(Long bookingId);

    List<RideOffer> findByBookingIdAndStatus(Long bookingId, OfferStatus status);

    boolean existsByBookingIdAndStatus(Long bookingId, OfferStatus status);

    boolean existsByDriverIdAndStatusAndExpiresAtAfter(Long driverId, OfferStatus status, Instant now);

    List<RideOffer> findByDriverIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
        Long driverId, OfferStatus status, Instant now);

    Optional<RideOffer> findFirstByBookingIdAndDriverIdAndStatusAndExpiresAtAfter(
        Long bookingId, Long driverId, OfferStatus status, Instant now);

    List<RideOffer> findByStatusAndExpiresAtBefore(OfferStatus status, Instant now);
}
