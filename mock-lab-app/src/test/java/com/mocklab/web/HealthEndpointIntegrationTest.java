package com.mocklab.web;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application on a random port (real servlet container, real
 * {@code DataSeeder}, real readiness state) and calls {@code GET /health}
 * over HTTP with no session and no credentials, per functional-spec.md §2.9
 * ("a simple readiness check, which does not need an access key, reports that
 * it is ready") and technical-considerations.md §4 "Readiness".
 *
 * <p>If the interceptor exclusion were missing, the client would be sent to
 * {@code /login} (and may follow that redirect to a 200 HTML page), so the
 * assertions check the JSON body and content type, not just the status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    // @regression
    @Test
    void healthReturns200UpWithoutCredentials() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }
}
