package com.labflow.client.http;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.common.config.ApiKey;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the parts of {@link LabRequestSpecs} that a stub lab cannot observe: the
 * timeouts (a hung lab must not hang a run), the JSON content negotiation and the header
 * rules. RestAssured's own specification is inspected without sending anything.
 *
 * @spec: 003-test-automation-foundation
 */
class LabRequestSpecsTest {

    private static FilterableRequestSpecification inspect(RequestSpecification spec) {
        return (FilterableRequestSpecification) SpecificationQuerier.query(spec);
    }

    @Test
    // @regression
    @DisplayName("Every request has a 5 second connect timeout and a 10 second read timeout")
    void requestsHaveTimeouts() {
        FilterableRequestSpecification spec =
                inspect(LabRequestSpecs.create("http://127.0.0.1:1", new ApiKey("any-key")));

        Map<String, ?> params = spec.getConfig().getHttpClientConfig().params();

        assertThat(params.get("http.connection.timeout")).isEqualTo(5_000);
        assertThat(params.get("http.socket.timeout")).isEqualTo(10_000);
    }

    @Test
    // @regression
    @DisplayName("Requests are addressed to the given lab and ask for and send JSON")
    void requestsAreJsonToTheGivenLab() {
        FilterableRequestSpecification spec =
                inspect(LabRequestSpecs.create("http://127.0.0.1:4321", new ApiKey("any-key")));

        assertThat(spec.getBaseUri()).isEqualTo("http://127.0.0.1:4321");
        assertThat(spec.getContentType()).startsWith("application/json");
        assertThat(spec.getHeaders().getValue("Accept")).startsWith("application/json");
    }

    @Test
    // @regression
    @DisplayName("The Authorization header is Bearer plus the key when a key is given, and absent otherwise")
    void authorizationHeaderOnlyWhenAKeyIsGiven() {
        FilterableRequestSpecification withKey =
                inspect(LabRequestSpecs.create("http://127.0.0.1:1", new ApiKey("abc-123")));
        FilterableRequestSpecification withoutKey = inspect(LabRequestSpecs.create("http://127.0.0.1:1", null));

        assertThat(withKey.getHeaders().getValue("Authorization")).isEqualTo("Bearer abc-123");
        assertThat(withoutKey.getHeaders().hasHeaderWithName("Authorization")).isFalse();
    }
}
