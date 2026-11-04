package com.example.bookingservice.repository;

import com.example.entityservice.models.Booking;
import com.example.entityservice.models.Booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    List<Booking> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<BookingStatus> statuses);

    Optional<Booking> findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(
        Long driverId, Collection<BookingStatus> statuses);

    List<Booking> findByStatusAndCreatedAtAfter(BookingStatus status, LocalDateTime after);
}
