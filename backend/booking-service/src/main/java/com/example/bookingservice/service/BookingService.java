package com.example.bookingservice.service;

import com.example.bookingservice.client.LocationClient;
import com.example.bookingservice.client.LocationClient.NearbyDriver;
import com.example.bookingservice.client.SocketClient;
import com.example.bookingservice.dto.BookingDtos.AcceptBookingRequest;
import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.BookingDtos.CreateBookingRequest;
import com.example.bookingservice.event.BookingEvent;
import com.example.bookingservice.event.BookingEventPublisher;
import com.example.bookingservice.repository.BookingRepository;
import com.example.entityservice.models.Booking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BookingService {

    private final BookingRepository repository;
    private final LocationClient locationClient;
    private final SocketClient socketClient;
    private final BookingEventPublisher events;
    private final double searchRadiusKm;

    public BookingService(BookingRepository repository,
                          LocationClient locationClient,
                          SocketClient socketClient,
                          BookingEventPublisher events,
                          @Value("${booking.search.radius-km:5}") double searchRadiusKm) {
        this.repository = repository;
        this.locationClient = locationClient;
        this.socketClient = socketClient;
        this.events = events;
        this.searchRadiusKm = searchRadiusKm;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        Booking booking = Booking.builder()
            .status(Booking.BookingStatus.PENDING)
            .passengerId(request.passengerId())
            .pickupLatitude(request.pickupLatitude())
            .pickupLongitude(request.pickupLongitude())
            .dropLatitude(request.dropLatitude())
            .dropLongitude(request.dropLongitude())
            .build();
        booking = repository.save(booking);

        List<NearbyDriver> drivers = locationClient.findNearby(
            request.pickupLatitude(), request.pickupLongitude());

        if (drivers == null || drivers.isEmpty()) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancelReason("NO_DRIVERS_NEARBY");
            booking = repository.save(booking);
            events.publish(BookingEvent.of(booking.getId(), "BOOKING_CANCELLED",
                booking.getPassengerId(), null, booking.getCancelReason()));
            throw new ResponseStatusException(CONFLICT, "No drivers nearby");
        }

        long candidateDriverId = parseDriverId(drivers.get(0).driverId());
        socketClient.notifyDriver(candidateDriverId, booking.getId(), booking.getPassengerId(),
            booking.getPickupLatitude(), booking.getPickupLongitude());
        events.publish(BookingEvent.of(booking.getId(), "DRIVER_NOTIFIED",
            booking.getPassengerId(), candidateDriverId, null));

        return BookingResponse.from(booking, candidateDriverId);
    }

    /** Called by socket-service when a driver accepts. The version column makes a second accept fail. */
    @Transactional
    public BookingResponse accept(Long bookingId, AcceptBookingRequest request) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", not waiting for a driver");
        }

        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.setDriverId(request.driverId());
        try {
            booking = repository.saveAndFlush(booking);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(CONFLICT, "Another driver already accepted this ride");
        }

        events.publish(BookingEvent.of(booking.getId(), "BOOKING_CONFIRMED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    /** Called when the ride is over. Only a CONFIRMED booking can be completed. */
    @Transactional
    public BookingResponse complete(Long bookingId) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(CONFLICT,
                "Booking is " + booking.getStatus() + ", only a CONFIRMED booking can be completed");
        }

        booking.setStatus(Booking.BookingStatus.COMPLETED);
        booking = repository.save(booking);
        events.publish(BookingEvent.of(booking.getId(), "BOOKING_COMPLETED",
            booking.getPassengerId(), booking.getDriverId(), null));
        return BookingResponse.from(booking, booking.getDriverId());
    }

    @Transactional
    public BookingResponse cancel(Long bookingId, String reason) {
        Booking booking = repository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found"));

        if (booking.getStatus() == Booking.BookingStatus.COMPLETED
            || booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new ResponseStatusException(CONFLICT, "Booking is already " + booking.getStatus());
        }

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

    private static long parseDriverId(String driverId) {
        try {
            return Long.parseLong(driverId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(CONFLICT,
                "Driver id must be numeric for booking (got " + driverId + ")");
        }
    }
}
