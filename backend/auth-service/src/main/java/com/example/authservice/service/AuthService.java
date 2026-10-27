package com.example.authservice.service;

import com.example.authservice.dto.AuthDtos.LoginRequest;
import com.example.authservice.dto.AuthDtos.LoginResponse;
import com.example.authservice.dto.AuthDtos.PassengerResponse;
import com.example.authservice.dto.AuthDtos.SignupRequest;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.repository.PassengerRepository;
import com.example.entityservice.models.Passenger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PassengerRepository passengerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(PassengerRepository passengerRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.passengerRepository = passengerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public PassengerResponse signup(SignupRequest request) {
        String email = normalise(request.email());
        if (passengerRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Passenger passenger = Passenger.builder()
            .name(request.name().trim())
            .email(email)
            .password(passwordEncoder.encode(request.password()))
            .build();

        return toResponse(passengerRepository.save(passenger));
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalise(request.email());
        Passenger passenger = passengerRepository.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);

        // same exception for unknown email and wrong password, so the API doesn't reveal which emails exist
        if (!passwordEncoder.matches(request.password(), passenger.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(passenger.getEmail());
        return new LoginResponse(token, "Bearer", jwtService.getExpiryMs(), passenger.getEmail());
    }

    public PassengerResponse profile(String email) {
        return passengerRepository.findByEmail(email)
            .map(this::toResponse)
            .orElseThrow(InvalidCredentialsException::new);
    }

    private String normalise(String email) {
        return email.trim().toLowerCase();
    }

    private PassengerResponse toResponse(Passenger p) {
        return new PassengerResponse(p.getId(), p.getName(), p.getEmail());
    }
}
