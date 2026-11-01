package com.example.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DriverTokenService {
    private final SecretKey key;
    private final long expiryMs;
    public DriverTokenService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiry-ms}") long expiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = expiryMs;
    }
    public String generate(Long id, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(email).claim("role", "DRIVER").claim("userId", id)
            .issuedAt(new Date(now)).expiration(new Date(now + expiryMs)).signWith(key).compact();
    }
    public Claims read(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
    public long getExpiryMs() { return expiryMs; }
}
