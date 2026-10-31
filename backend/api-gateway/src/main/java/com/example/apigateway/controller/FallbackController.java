package com.example.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** JSON fallbacks when a circuit breaker opens because a service keeps failing. */
@RestController
public class FallbackController {

    // [a-z]+ keeps odd characters out of the JSON body
    @RequestMapping("/fallback/{service:[a-z]+}")
    public Mono<Void> fallback(@PathVariable("service") String service, ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String json = "{\"error\":\"" + service + " service is temporarily unavailable\"}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
