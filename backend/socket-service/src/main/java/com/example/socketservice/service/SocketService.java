package com.example.socketservice.service;

import com.example.socketservice.dto.SocketDtos.BookingUpdate;
import com.example.socketservice.dto.SocketDtos.NotifyDriverRequest;
import com.example.socketservice.dto.SocketDtos.RideRequestMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public SocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyDriver(NotifyDriverRequest request) {
        RideRequestMessage message = new RideRequestMessage(
            request.bookingId(), request.passengerId(), request.pickupLatitude(), request.pickupLongitude());
        messagingTemplate.convertAndSend("/topic/drivers/" + request.driverId() + "/rides", message);
    }

    public void publishBookingUpdate(BookingUpdate update) {
        messagingTemplate.convertAndSend("/topic/bookings/" + update.bookingId(), update);
    }
}
