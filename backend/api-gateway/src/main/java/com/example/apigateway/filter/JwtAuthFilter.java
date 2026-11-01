package com.example.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_AUTH = List.of("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/signup/driver", "/api/v1/auth/login/driver");
    private final SecretKey key;
    public JwtAuthFilter(@Value("${jwt.secret}") String secret) { key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_AUTH.contains(path) || path.equals("/actuator") || path.startsWith("/actuator/") || path.startsWith("/fallback/")) return chain.filter(exchange);
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7)).getPayload();
            if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getExpiration() == null) return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid token identity or expiry");
            String role = claims.get("role", String.class);
            if (role != null && !role.equals("DRIVER") && !role.equals("PASSENGER")) return reject(exchange, HttpStatus.UNAUTHORIZED, "Unknown token role");
            boolean driver = "DRIVER".equals(role);
            if (driver && !(claims.get("userId") instanceof Number)) return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing driver identity");
            boolean driverPath = path.equals("/api/v1/auth/driver/me");
            if (driver != driverPath) return reject(exchange, HttpStatus.FORBIDDEN, "This account cannot access this endpoint");
            ServerWebExchange downstream = exchange.mutate().request(exchange.getRequest().mutate().headers(headers -> {
                headers.remove("X-User-Id"); headers.remove("X-User-Role");
                headers.set("X-User-Email", claims.getSubject());
            }).build()).build();
            return chain.filter(downstream);
        } catch (JwtException | IllegalArgumentException e) { return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token"); }
    }
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
    @Override public int getOrder() { return -100; }
}
