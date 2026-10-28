package com.example.socketservice.controller;

import com.example.socketservice.dto.SocketDtos.NotifyDriverRequest;
import com.example.socketservice.service.SocketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST entry point used by other services to push a ride request to a driver. */
@RestController
@RequestMapping("/api/v1/socket")
public class SocketRestController {

    private final SocketService socketService;

    public SocketRestController(SocketService socketService) {
        this.socketService = socketService;
    }

    @PostMapping("/rides/notify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void notifyDriver(@Valid @RequestBody NotifyDriverRequest request) {
        socketService.notifyDriver(request);
    }
}
