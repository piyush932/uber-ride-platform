package com.example.authservice.service;

import com.example.authservice.dto.AuthDtos.LoginRequest;
import com.example.authservice.dto.AuthDtos.SignupRequest;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.repository.DriverRepository;
import com.example.entityservice.models.Driver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DriverAuthServiceTest {
    private final DriverRepository repository = mock(DriverRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final DriverTokenService tokens = new DriverTokenService("dev-only-secret-change-me-dev-only-secret-change-me", 60000);
    private final DriverAuthService service = new DriverAuthService(repository, passwords, tokens);
    @Test void signupHashesPasswordAndNormalisesEmail() {
        when(repository.saveAndFlush(any(Driver.class))).thenAnswer(inv -> { Driver d = inv.getArgument(0); d.setId(7L); return d; });
        var profile = service.signup(new SignupRequest(" Driver ", "DRIVER@example.com", "secret123"));
        assertEquals("driver@example.com", profile.email()); assertEquals("DRIVER", profile.role());
        var captor = org.mockito.ArgumentCaptor.forClass(Driver.class); verify(repository).saveAndFlush(captor.capture());
        assertTrue(passwords.matches("secret123", captor.getValue().getPassword()));
    }
    @Test void loginIssuesSignedDriverIdentity() {
        Driver d = Driver.builder().name("Driver").email("driver@example.com").password(passwords.encode("secret123")).build(); d.setId(7L);
        when(repository.findByEmail("driver@example.com")).thenReturn(Optional.of(d));
        var claims = tokens.read(service.login(new LoginRequest("driver@example.com", "secret123")).token());
        assertEquals("DRIVER", claims.get("role")); assertEquals(7L, ((Number) claims.get("userId")).longValue());
    }
    @Test void wrongPasswordIsRejected() {
        Driver d = Driver.builder().name("Driver").email("driver@example.com").password(passwords.encode("secret123")).build();
        when(repository.findByEmail("driver@example.com")).thenReturn(Optional.of(d));
        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("driver@example.com", "wrong123")));
    }
    @Test void unknownAccountIsRejected() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("absent@example.com", "secret123")));
    }
    @Test void profileNeverReturnsPassword() {
        Driver d = Driver.builder().name("Driver").email("driver@example.com").password("private-hash").build(); d.setId(7L);
        when(repository.findByEmail("driver@example.com")).thenReturn(Optional.of(d));
        assertEquals(new DriverAuthService.DriverProfile(7L, "Driver", "driver@example.com", "DRIVER"), service.profile("driver@example.com"));
    }
}
