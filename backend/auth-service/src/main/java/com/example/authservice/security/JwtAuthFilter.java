package com.example.authservice.security;

import com.example.authservice.repository.DriverRepository;
import com.example.authservice.service.DriverTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final DriverTokenService tokens;
    private final DriverRepository drivers;
    private final UserDetailsService passengers;
    public JwtAuthFilter(DriverTokenService tokens, DriverRepository drivers, UserDetailsService passengers) {
        this.tokens = tokens; this.drivers = drivers; this.passengers = passengers;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokens.read(header.substring(7));
                String email = claims.getSubject();
                if (email == null || email.isBlank() || claims.getExpiration() == null) throw new IllegalArgumentException("Missing identity or expiry");
                String role = claims.get("role", String.class);
                if ("DRIVER".equals(role)) {
                    var driver = drivers.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Unknown driver"));
                    Object id = claims.get("userId");
                    if (!(id instanceof Number) || driver.getId().longValue() != ((Number) id).longValue()) throw new IllegalArgumentException("Driver identity mismatch");
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));
                } else if (role == null || "PASSENGER".equals(role)) {
                    UserDetails passenger = passengers.loadUserByUsername(email);
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(passenger, null, List.of(new SimpleGrantedAuthority("ROLE_PASSENGER"))));
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
