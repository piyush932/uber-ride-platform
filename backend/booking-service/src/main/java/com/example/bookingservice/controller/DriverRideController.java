package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingDtos.BookingResponse;
import com.example.bookingservice.dto.DriverDtos.DashboardResponse;
import com.example.bookingservice.service.DriverRideService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The driver id comes from X-User-Id and X-User-Role, which the API gateway sets from the verified JWT
 * and strips from client requests. Never expose this service port to the internet.
 */
@RestController
@RequestMapping("/api/v1/driver")
public class DriverRideController {

    private final DriverRideService service;

    public DriverRideController(DriverRideService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestHeader("X-User-Id") Long userId,
                                       @RequestHeader("X-User-Role") String role) {
        return service.dashboard(driverId(userId, role));
    }

    @PostMapping("/offers/{id}/accept")
    public BookingResponse accept(@PathVariable("id") Long id,
                                  @RequestHeader("X-User-Id") Long userId,
                                  @RequestHeader("X-User-Role") String role) {
        return service.acceptOffer(driverId(userId, role), id);
    }

    @PostMapping("/offers/{id}/reject")
    public BookingResponse reject(@PathVariable("id") Long id,
                                  @RequestHeader("X-User-Id") Long userId,
                                  @RequestHeader("X-User-Role") String role) {
        return service.rejectOffer(driverId(userId, role), id);
    }

    @PostMapping("/rides/{id}/start")
    public BookingResponse start(@PathVariable("id") Long id,
                                 @RequestHeader("X-User-Id") Long userId,
                                 @RequestHeader("X-User-Role") String role) {
        return service.start(driverId(userId, role), id);
    }

    @PostMapping("/rides/{id}/complete")
    public BookingResponse complete(@PathVariable("id") Long id,
                                    @RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role) {
        return service.complete(driverId(userId, role), id);
    }

    @GetMapping("/rides")
    public List<BookingResponse> history(@RequestHeader("X-User-Id") Long userId,
                                         @RequestHeader("X-User-Role") String role) {
        return service.history(driverId(userId, role));
    }

    private static long driverId(Long userId, String role) {
        if (!"DRIVER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver role required");
        }
        return userId;
    }
}
