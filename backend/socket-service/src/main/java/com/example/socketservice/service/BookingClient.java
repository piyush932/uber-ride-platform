package com.example.socketservice.service;

/** Talks to the booking service. Kept behind an interface so the socket code can be tested alone. */
public interface BookingClient {

    /** Asks the booking service to confirm the ride for this driver. True if it was confirmed. */
    boolean accept(long bookingId, long driverId);
}
