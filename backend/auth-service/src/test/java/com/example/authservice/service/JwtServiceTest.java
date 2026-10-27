package com.example.authservice.service;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void tokenRoundTripKeepsSubject() {
        String token = jwtService.generateToken("asha@example.com");

        assertEquals("asha@example.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isValid(token, "asha@example.com"));
    }

    @Test
    void tokenIsRejectedForDifferentUser() {
        String token = jwtService.generateToken("asha@example.com");

        assertFalse(jwtService.isValid(token, "someone-else@example.com"));
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService shortLived = new JwtService(SECRET, -1_000);
        String token = shortLived.generateToken("asha@example.com");

        assertFalse(shortLived.isValid(token, "asha@example.com"));
        assertThrows(JwtException.class, () -> shortLived.extractUsername(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateToken("asha@example.com");
        String[] parts = token.split("\\.");
        // change the first character of the signature, which always alters the signature bytes
        char first = parts[2].charAt(0);
        String badSignature = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + badSignature;

        assertFalse(jwtService.isValid(tampered, "asha@example.com"));
    }

    @Test
    void tokenSignedWithAnotherSecretIsInvalid() {
        JwtService other = new JwtService("another-secret-another-secret-another-1", 60_000);
        String foreign = other.generateToken("asha@example.com");

        assertFalse(jwtService.isValid(foreign, "asha@example.com"));
    }
}
