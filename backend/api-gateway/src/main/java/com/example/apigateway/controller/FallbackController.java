package com.example.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** JSON fallbacks when a circuit breaker opens because a service keeps failing. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<Void> fallback(@PathVariable("service") String service, ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"\" + service + " service is temporarily unavailable\"}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
