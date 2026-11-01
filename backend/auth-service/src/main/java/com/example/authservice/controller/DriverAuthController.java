package com.example.authservice.controller;

import com.example.authservice.dto.AuthDtos.LoginRequest;
import com.example.authservice.dto.AuthDtos.LoginResponse;
import com.example.authservice.dto.AuthDtos.SignupRequest;
import com.example.authservice.service.DriverAuthService;
import com.example.authservice.service.DriverAuthService.DriverProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class DriverAuthController {
    private final DriverAuthService service;
    public DriverAuthController(DriverAuthService service) { this.service = service; }
    @PostMapping("/signup/driver")
    public ResponseEntity<DriverProfile> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.signup(request));
    }
    @PostMapping("/login/driver")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
    @GetMapping("/driver/me")
    public DriverProfile me(Authentication authentication) { return service.profile(authentication.getName()); }
}
