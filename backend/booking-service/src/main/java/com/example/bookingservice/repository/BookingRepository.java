package com.example.bookingservice.repository;

import com.example.entityservice.models.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);
}
