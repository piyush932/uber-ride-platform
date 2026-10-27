package com.example.authservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Entities live in the shared entity-service library, so they have to be scanned explicitly.
@Configuration
@EnableJpaAuditing
@EntityScan("com.example.entityservice.models")
@EnableJpaRepositories("com.example.authservice.repository")
public class JpaConfig {
}
