package com.example.apigateway.config;

import com.example.apigateway.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The gateway checks the JWT for every route except the ones a new user needs
 * (signup, login) and health/fallback endpoints. It does not replace the
 * services' own rules; it stops unauthenticated traffic before it reaches them.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, JwtAuthFilter jwtAuthFilter) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                .pathMatchers("/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                .pathMatchers("/actuator/**", "/fallback/**").permitAll()
                .anyExchange().authenticated())
            .addFilterBefore(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
}
