package com.example.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    private JwtAuthFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String token(String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(key)
            .compact();
    }

    @Test
    void publicSignupPathIsAllowedWithoutAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/signup").build());

        filter.filter(exchange, chain).block();

        assertEquals(200, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void protectedPathWithoutATokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validTokenIsForwardedWithTheUserEmail() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer " + token("rider@example.com"))
                .build());

        filter.filter(exchange, chain).block();

        assertEquals("rider@example.com",
            exchange.getRequest().getHeaders().getFirst("X-User-Email"));
    }

    @Test
    void garbageTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer not-a-token")
                .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void expiredTokenReturns401() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
            .subject("rider@example.com")
            .issuedAt(new Date(System.currentTimeMillis() - 10000))
            .expiration(new Date(System.currentTimeMillis() - 5000))
            .signWith(key)
            .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer " + expired)
                .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void actuatorIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health").build());

        filter.filter(exchange, chain).block();

        assertEquals(200, exchange.getResponse().getStatusCode().value());
    }
}
