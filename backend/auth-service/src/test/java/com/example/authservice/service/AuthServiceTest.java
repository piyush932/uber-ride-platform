package com.example.authservice.service;

import com.example.authservice.dto.AuthDtos.LoginRequest;
import com.example.authservice.dto.AuthDtos.LoginResponse;
import com.example.authservice.dto.AuthDtos.PassengerResponse;
import com.example.authservice.dto.AuthDtos.SignupRequest;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.repository.PassengerRepository;
import com.example.entityservice.models.Passenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("unit-test-secret-unit-test-secret-1234", 60_000);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(passengerRepository, passwordEncoder, jwtService);
    }

    @Test
    void signupStoresHashedPasswordAndLowercasedEmail() {
        when(passengerRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(passengerRepository.save(any(Passenger.class))).thenAnswer(inv -> {
            Passenger p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });

        PassengerResponse response = authService.signup(new SignupRequest("Asha", "  Asha@Example.com ", "secret123"));

        ArgumentCaptor<Passenger> saved = ArgumentCaptor.forClass(Passenger.class);
        verify(passengerRepository).save(saved.capture());
        assertEquals("asha@example.com", saved.getValue().getEmail());
        assertNotEquals("secret123", saved.getValue().getPassword());
        assertTrue(passwordEncoder.matches("secret123", saved.getValue().getPassword()));
        assertEquals(7L, response.id());
        assertEquals("Asha", response.name());
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(passengerRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
            () -> authService.signup(new SignupRequest("Asha", "asha@example.com", "secret123")));
        verify(passengerRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForCorrectPassword() {
        Passenger stored = Passenger.builder()
            .name("Asha")
            .email("asha@example.com")
            .password(passwordEncoder.encode("secret123"))
            .build();
        when(passengerRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(stored));

        LoginResponse response = authService.login(new LoginRequest("asha@example.com", "secret123"));

        assertEquals("Bearer", response.tokenType());
        assertEquals("asha@example.com", jwtService.extractUsername(response.token()));
    }

    @Test
    void loginFailsForWrongPassword() {
        Passenger stored = Passenger.builder()
            .name("Asha")
            .email("asha@example.com")
            .password(passwordEncoder.encode("secret123"))
            .build();
        when(passengerRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(stored));

        assertThrows(InvalidCredentialsException.class,
            () -> authService.login(new LoginRequest("asha@example.com", "wrong-password")));
    }

    @Test
    void loginFailsForUnknownEmail() {
        when(passengerRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
            () -> authService.login(new LoginRequest("ghost@example.com", "whatever1")));
    }
}
