package com.example.bookingservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker for booking -> socket calls. If socket-service is down or slow,
 * the booking still stays PENDING instead of hanging or failing.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Resilience4JCircuitBreakerFactory defaultCustomizer() {
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory();
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build())
            .timeLimiterConfig(io.github.resilience4j.timelimiter.TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build())
            .build());
        return factory;
    }
}
