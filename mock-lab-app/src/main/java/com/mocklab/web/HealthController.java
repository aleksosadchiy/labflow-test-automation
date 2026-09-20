package com.mocklab.web;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated readiness check (functional-spec.md §2.9), deliberately
 * custom rather than Spring Boot Actuator so no new dependency is needed.
 *
 * <p>It reports 200 {@code {"status":"UP"}} only when the framework's
 * readiness state is {@link ReadinessState#ACCEPTING_TRAFFIC}. Spring Boot
 * flips that state only after {@code ApplicationReadyEvent}, which fires
 * <em>after</em> every {@code CommandLineRunner} (including the
 * {@code DataSeeder}) has completed. The HTTP port opens earlier than that,
 * so this is a truer signal than "{@code /login} answers". Until then it
 * returns 503 {@code {"status":"STARTING"}}, which {@code curl --retry}
 * treats as transient and retries.
 *
 * <p>{@code /health} is excluded from the session interceptor in
 * {@code WebConfig}, so callers get 200/503 rather than a redirect to
 * {@code /login}.
 */
@RestController
public class HealthController {

    private final ApplicationAvailability availability;

    public HealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> health() {
        boolean ready = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity
                .status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", ready ? "UP" : "STARTING"));
    }
}
