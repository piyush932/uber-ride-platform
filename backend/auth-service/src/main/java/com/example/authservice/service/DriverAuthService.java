package com.example.authservice.service;

import com.example.authservice.dto.AuthDtos.LoginRequest;
import com.example.authservice.dto.AuthDtos.LoginResponse;
import com.example.authservice.dto.AuthDtos.SignupRequest;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.repository.DriverRepository;
import com.example.entityservice.models.Driver;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverAuthService {
    public record DriverProfile(Long id, String name, String email, String role) {}
    private final DriverRepository repository;
    private final PasswordEncoder passwords;
    private final DriverTokenService tokens;
    public DriverAuthService(DriverRepository repository, PasswordEncoder passwords, DriverTokenService tokens) {
        this.repository = repository; this.passwords = passwords; this.tokens = tokens;
    }
    @Transactional
    public DriverProfile signup(SignupRequest request) {
        String email = normalise(request.email());
        if (repository.existsByEmail(email)) throw new EmailAlreadyExistsException(email);
        Driver driver = Driver.builder().name(request.name().trim()).email(email)
            .password(passwords.encode(request.password())).build();
        try { return profileOf(repository.saveAndFlush(driver)); }
        catch (DataIntegrityViolationException e) { throw new EmailAlreadyExistsException(email); }
    }
    public LoginResponse login(LoginRequest request) {
        Driver driver = repository.findByEmail(normalise(request.email())).orElseThrow(InvalidCredentialsException::new);
        if (!passwords.matches(request.password(), driver.getPassword())) throw new InvalidCredentialsException();
        return new LoginResponse(tokens.generate(driver.getId(), driver.getEmail()), "Bearer", tokens.getExpiryMs(), driver.getEmail());
    }
    public DriverProfile profile(String email) {
        return repository.findByEmail(email).map(this::profileOf).orElseThrow(InvalidCredentialsException::new);
    }
    private DriverProfile profileOf(Driver driver) { return new DriverProfile(driver.getId(), driver.getName(), driver.getEmail(), "DRIVER"); }
    private String normalise(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
