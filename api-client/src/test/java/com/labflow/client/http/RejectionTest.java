package com.labflow.client.http;

// @layer: unit
// @spec: 003-test-automation-foundation

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the status the lab uses for each kind of refusal (functional-spec.md §2.6), typed out
 * as literals so a wrong mapping in {@link Rejection} fails here.
 *
 * @spec: 003-test-automation-foundation
 */
class RejectionTest {

    @Test
    // @regression
    @DisplayName("Each kind of refusal maps to the lab's status: 400, 401, 404, 405, 409")
    void eachKindHasTheLabsStatus() {
        assertThat(Rejection.INVALID_INPUT.status()).isEqualTo(400);
        assertThat(Rejection.UNAUTHORIZED.status()).isEqualTo(401);
        assertThat(Rejection.NOT_FOUND.status()).isEqualTo(404);
        assertThat(Rejection.METHOD_NOT_ALLOWED.status()).isEqualTo(405);
        assertThat(Rejection.CONFLICT.status()).isEqualTo(409);
    }

    @Test
    // @regression
    @DisplayName("There are exactly five kinds of refusal")
    void thereAreFiveKinds() {
        assertThat(Rejection.values()).hasSize(5);
    }
}
