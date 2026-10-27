package com.example.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMs;

    // secret must be at least 32 bytes for HS256
    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiry-ms}") long expiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = expiryMs;
    }

    public String generateToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(subject)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expiryMs))
            .signWith(key)
            .compact();
    }

    /** Throws JwtException if the token is expired, malformed or has a bad signature. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token, String expectedUsername) {
        try {
            Claims claims = parse(token);
            return expectedUsername.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpiryMs() {
        return expiryMs;
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
