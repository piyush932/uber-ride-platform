package com.example.apigateway.filter;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String token(String email, long expiresInMs) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date(System.currentTimeMillis() - 10000))
            .expiration(new Date(System.currentTimeMillis() + expiresInMs))
            .signWith(key())
            .compact();
    }

    private String roleToken(String role, Long userId) {
        JwtBuilder builder = Jwts.builder()
            .subject("user@example.com")
            .claim("role", role)
            .issuedAt(new Date(System.currentTimeMillis() - 10000))
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(key());
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.compact();
    }

    private String driverToken(long id) {
        return roleToken("DRIVER", id);
    }

    private String passengerToken() {
        return roleToken("PASSENGER", 5L);
    }

    private MockServerWebExchange call(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void publicSignupPathIsPassedOnWithoutAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/signup").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedPathWithoutATokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void validTokenIsForwardedWithTheUserEmail() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer " + token("rider@example.com", 3600000))
                .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertEquals("rider@example.com",
            forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Email"));
    }

    @Test
    void garbageTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer not-a-token")
                .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void expiredTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header("Authorization", "Bearer " + token("rider@example.com", -5000))
                .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void actuatorIsPassedOnWithoutAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void driverMayCallTheDriverApiAndTheGatewaySetsTheIdentityHeaders() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.get("/api/v1/driver/dashboard")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());

        assertNull(exchange.getResponse().getStatusCode());
        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertEquals("9001", forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("DRIVER", forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("user@example.com", forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Email"));
    }

    @Test
    void passengerCannotCallTheDriverApi() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.get("/api/v1/driver/dashboard")
            .header("Authorization", "Bearer " + passengerToken())
            .build());

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void passengerCannotUseTheDriverProfileEndpoint() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.get("/api/v1/auth/driver/me")
            .header("Authorization", "Bearer " + passengerToken())
            .build());

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void driverMayUseItsOwnProfileEndpoint() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.get("/api/v1/auth/driver/me")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());

        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void driverMayWriteOnlyItsOwnLocation() {
        MockServerWebExchange own = call(MockServerHttpRequest.put("/api/v1/locations/drivers/9001")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());
        MockServerWebExchange ownDelete = call(MockServerHttpRequest.delete("/api/v1/locations/drivers/9001")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());
        MockServerWebExchange other = call(MockServerHttpRequest.put("/api/v1/locations/drivers/9002")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());

        assertNull(own.getResponse().getStatusCode());
        assertNull(ownDelete.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, other.getResponse().getStatusCode());
    }

    @Test
    void driverCannotUsePassengerRoutesOrReadNearbyDrivers() {
        MockServerWebExchange bookings = call(MockServerHttpRequest.get("/api/v1/bookings")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());
        MockServerWebExchange nearby = call(MockServerHttpRequest.get("/api/v1/locations/nearby")
            .header("Authorization", "Bearer " + driverToken(9001))
            .build());

        assertEquals(HttpStatus.FORBIDDEN, bookings.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, nearby.getResponse().getStatusCode());
    }

    @Test
    void passengerCannotWriteADriverLocation() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.put("/api/v1/locations/drivers/9001")
            .header("Authorization", "Bearer " + passengerToken())
            .build());

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void passengerCannotUseTheLegacyAcceptAndCompleteEndpoints() {
        MockServerWebExchange accept = call(MockServerHttpRequest.post("/api/v1/bookings/1/accept")
            .header("Authorization", "Bearer " + passengerToken())
            .build());
        MockServerWebExchange complete = call(MockServerHttpRequest.post("/api/v1/bookings/1/complete")
            .header("Authorization", "Bearer " + passengerToken())
            .build());

        assertEquals(HttpStatus.FORBIDDEN, accept.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, complete.getResponse().getStatusCode());
    }

    @Test
    void passengerMayReadNearbyDriversAndBookRides() {
        MockServerWebExchange nearby = call(MockServerHttpRequest.get("/api/v1/locations/nearby")
            .header("Authorization", "Bearer " + passengerToken())
            .build());
        MockServerWebExchange booking = call(MockServerHttpRequest.post("/api/v1/bookings")
            .header("Authorization", "Bearer " + passengerToken())
            .build());

        assertNull(nearby.getResponse().getStatusCode());
        assertNull(booking.getResponse().getStatusCode());
    }

    @Test
    void aDriverTokenWithoutAUserIdIsRejected() {
        MockServerWebExchange exchange = call(MockServerHttpRequest.get("/api/v1/driver/dashboard")
            .header("Authorization", "Bearer " + roleToken("DRIVER", null))
            .build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void clientSentIdentityHeadersAreReplacedByTheGateway() {
        call(MockServerHttpRequest.get("/api/v1/bookings")
            .header("Authorization", "Bearer " + roleToken("PASSENGER", null))
            .header("X-User-Id", "1")
            .header("X-User-Role", "DRIVER")
            .build());

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertEquals("PASSENGER", forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
        assertNull(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void clientSentIdentityHeadersAreStrippedOnPublicPaths() {
        call(MockServerHttpRequest.post("/api/v1/auth/login")
            .header("X-User-Id", "1")
            .header("X-User-Role", "DRIVER")
            .build());

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertNull(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
    }
}
