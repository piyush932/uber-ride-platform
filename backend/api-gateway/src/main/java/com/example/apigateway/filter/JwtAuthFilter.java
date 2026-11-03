package com.example.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_AUTH = List.of(
        "/api/v1/auth/signup", "/api/v1/auth/login",
        "/api/v1/auth/signup/driver", "/api/v1/auth/login/driver");
    private static final Pattern LOCATION = Pattern.compile("^/api/v1/locations/drivers/([0-9]+)$");
    private static final Pattern LEGACY_ACTION = Pattern.compile("^/api/v1/bookings/[0-9]+/(accept|complete)$");
    private final SecretKey key;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange clean = exchange.mutate().request(exchange.getRequest().mutate()
            .headers(headers -> headers.keySet().removeIf(
                name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-")))
            .build()).build();
        String path = clean.getRequest().getPath().value();
        HttpMethod method = clean.getRequest().getMethod();
        if (PUBLIC_AUTH.contains(path) || path.equals("/actuator")
                || path.startsWith("/actuator/") || path.startsWith("/fallback/")) {
            return chain.filter(clean);
        }
        String header = clean.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(clean, HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(header.substring(7)).getPayload();
            if (claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getExpiration() == null) {
                return reject(clean, HttpStatus.UNAUTHORIZED, "Invalid token identity or expiry");
            }
            String role = claims.get("role", String.class);
            if (role != null && !role.equals("DRIVER") && !role.equals("PASSENGER")) {
                return reject(clean, HttpStatus.UNAUTHORIZED, "Unknown token role");
            }
            boolean driver = "DRIVER".equals(role);
            Object identity = claims.get("userId");
            if (driver && (!(identity instanceof Number) || ((Number) identity).longValue() <= 0)) {
                return reject(clean, HttpStatus.UNAUTHORIZED, "Missing driver identity");
            }
            boolean driverPath = path.equals("/api/v1/auth/driver/me")
                || path.equals("/api/v1/driver") || path.startsWith("/api/v1/driver/");
            Matcher location = LOCATION.matcher(path);
            boolean locationWrite = location.matches()
                && (HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method));
            if (driver) {
                boolean ownLocation = locationWrite
                    && location.group(1).equals(String.valueOf(((Number) identity).longValue()));
                if (!driverPath && !ownLocation) {
                    return reject(clean, HttpStatus.FORBIDDEN, "This account cannot access this endpoint");
                }
            } else if (driverPath || locationWrite || LEGACY_ACTION.matcher(path).matches()) {
                return reject(clean, HttpStatus.FORBIDDEN, "Driver role required");
            }
            ServerWebExchange downstream = clean.mutate().request(clean.getRequest().mutate()
                .headers(headers -> {
                    headers.set("X-User-Email", claims.getSubject());
                    headers.set("X-User-Role", driver ? "DRIVER" : "PASSENGER");
                    if (identity instanceof Number number) {
                        headers.set("X-User-Id", String.valueOf(number.longValue()));
                    }
                }).build()).build();
            return chain.filter(downstream);
        } catch (JwtException | IllegalArgumentException e) {
            return reject(clean, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override public int getOrder() { return -100; }
}
