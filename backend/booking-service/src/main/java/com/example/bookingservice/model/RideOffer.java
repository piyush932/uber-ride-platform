package com.example.bookingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One ride offered to one driver for a limited time. A booking has at most one OFFERED
 * offer at any moment; rejection and expiry move it on to the next driver.
 */
@Entity
@Table(name = "ride_offer")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideOffer {

    public enum OfferStatus {
        OFFERED, ACCEPTED, REJECTED, EXPIRED, WITHDRAWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // a driver accepting and the expiry job racing each other: one of them fails
    @Version
    private Long version;
}
