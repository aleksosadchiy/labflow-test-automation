package com.labflow.client.preflight;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LabPreflight}, the one-off "is the lab there and does it accept my key"
 * check run before any API check (functional-spec.md §2.3: a missing lab or a rejected key
 * stops the run with one clear message instead of many confusing failures; technical
 * spec §2.6). They run against a {@link StubLab}, so no real lab is needed and the
 * requests the pre-flight actually sent can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class LabPreflightTest {

    private static final String KEY = "s3cret-preflight-key-6013";
    private static final String BANNER = "LAB PRE-FLIGHT FAILED:";
    private static final String START_COMMAND = "cd mock-lab-app && ./mvnw spring-boot:run";
    private static final String HEALTH_UP = "{\"status\":\"UP\"}";

    private StubLab lab;

    @BeforeEach
    void startLab() {
        lab = StubLab.start();
    }

    @AfterEach
    void stopLab() {
        lab.close();
    }

    private LabConfig config(String baseUrl) {
        return new LabConfig(baseUrl, new ApiKey(KEY));
    }

    private void healthy() {
        lab.answer("GET", "/health", 200, HEALTH_UP);
        lab.answer("GET", "/api/samples", 200, "[]");
    }

    // ---- everything fine ----

    @Test
    // @regression
    @DisplayName("A lab that is up and accepts the key passes silently, having been asked /health without the key and /api/samples with it")
    void healthyLabPassesAndBothRequestsAreSentAsSpecified() {
        healthy();

        assertThatCode(() -> LabPreflight.check(config(lab.baseUrl()))).doesNotThrowAnyException();

        List<StubLab.ReceivedRequest> sent = lab.requests();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).method()).isEqualTo("GET");
        assertThat(sent.get(0).path()).isEqualTo("/health");
        assertThat(sent.get(0).headers()).doesNotContainKey("Authorization");
        assertThat(sent.get(1).method()).isEqualTo("GET");
        assertThat(sent.get(1).path()).isEqualTo("/api/samples");
        assertThat(sent.get(1).header("Authorization")).contains("Bearer " + KEY);
    }

    // ---- the lab is not reachable ----

    @Test
    // @regression
    @DisplayName("A lab that is not running fails fast with the banner, the address tried and the command to start it")
    void unreachableLabFailsFastWithAddressAndStartCommand() {
        String deadUrl = lab.baseUrl();
        lab.close(); // the port was bound a moment ago and is now closed: connection refused

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> LabPreflight.check(config(deadUrl)))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("cannot reach the lab at " + deadUrl)
                .hasMessageContaining(START_COMMAND)
                .hasMessageNotContaining(KEY);
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(took).as("a dead port must be reported quickly, with no retries or sleeps")
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    // @regression
    @DisplayName("A lab that answers /health with 503 (still starting) fails with the banner, the address, the start command and the 503")
    void healthAnswering503FailsWithBannerAddressStartCommandAndStatus() {
        lab.answer("GET", "/health", 503, "{\"status\":\"STARTING\"}");

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("cannot reach the lab at " + lab.baseUrl())
                .hasMessageContaining(START_COMMAND)
                .hasMessageContaining("503")
                .hasMessageNotContaining(KEY);
    }

    @Test
    // @regression
    @DisplayName("Something else answering on the address (/health is a 404) is treated as 'no lab here'")
    void healthAnswering404IsTreatedAsNoLab() {
        // nothing registered: the stub answers 404 to every path

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("cannot reach the lab at " + lab.baseUrl())
                .hasMessageContaining(START_COMMAND)
                .hasMessageContaining("404");
    }

    @Test
    // @regression
    @DisplayName("When /health fails the key is never sent anywhere, and /api/samples is not asked")
    void keyIsNotSentWhenHealthFails() {
        lab.answer("GET", "/health", 503, "{\"status\":\"STARTING\"}");
        lab.answer("GET", "/api/samples", 200, "[]");

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl()))).isInstanceOf(LabPreflightException.class);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().path()).isEqualTo("/health");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
    }

    @Test
    // @regression
    @DisplayName("A malformed address (no scheme) is reported as unreachable with the address and start command, not a raw JDK exception")
    void malformedAddressIsReportedAsUnreachable() {
        assertThatThrownBy(() -> LabPreflight.check(config("localhost:8080")))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("cannot reach the lab at localhost:8080")
                .hasMessageContaining(START_COMMAND)
                .hasMessageNotContaining(KEY);
    }

    // ---- the key is refused or the lab misbehaves ----

    @Test
    // @regression
    @DisplayName("A key the lab rejects (401) fails naming the address and API_TOKEN, and never printing the key")
    void rejectedKeyFailsNamingApiTokenAndNotTheKey() {
        lab.answer("GET", "/health", 200, HEALTH_UP);
        lab.answer("GET", "/api/samples", 401, "Unauthorized.");

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("the lab at " + lab.baseUrl() + " rejected the access key")
                .hasMessageContaining("API_TOKEN")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining(START_COMMAND);
    }

    @Test
    // @regression
    @DisplayName("Any other refusal on /api/samples (500) is reported as an unexpected answer, not as a bad key")
    void unexpectedSamplesAnswerIsReportedAsSuch() {
        lab.answer("GET", "/health", 200, HEALTH_UP);
        lab.answer("GET", "/api/samples", 500, "{\"message\":\"boom\"}");

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageStartingWith(BANNER)
                .hasMessageContaining("unexpected answer 500 from GET /api/samples at " + lab.baseUrl())
                .hasMessageNotContaining("rejected the access key")
                .hasMessageNotContaining("API_TOKEN")
                .hasMessageNotContaining(KEY);
    }

    @Test
    // @regression
    @DisplayName("A 403 on /api/samples is 'unexpected answer', because only 401 means the key was rejected")
    void forbiddenIsNotTreatedAsARejectedKey() {
        lab.answer("GET", "/health", 200, HEALTH_UP);
        lab.answer("GET", "/api/samples", 403, "{}");

        assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                .isInstanceOf(LabPreflightException.class)
                .hasMessageContaining("unexpected answer 403 from GET /api/samples at " + lab.baseUrl())
                .hasMessageNotContaining("rejected the access key");
    }

    // ---- nothing sensitive leaks ----

    @Test
    // @regression
    @DisplayName("No failure message contains the key, a header value or any part of a response body")
    void noFailureMessageContainsTheKeyHeadersOrBodies() {
        String bodyMarker = "body-marker-7781";
        String headerMarker = "header-marker-2290";
        Map<String, String> debugHeader = Map.of("X-Debug", headerMarker);
        List<Consumer<StubLab>> scenarios = List.of(
                own -> own.answer("GET", "/health", 503, bodyMarker, debugHeader),
                own -> {
                    own.answer("GET", "/health", 200, HEALTH_UP);
                    own.answer("GET", "/api/samples", 401, bodyMarker + " " + KEY, debugHeader);
                },
                own -> {
                    own.answer("GET", "/health", 200, HEALTH_UP);
                    own.answer("GET", "/api/samples", 500, bodyMarker + " " + KEY, debugHeader);
                });

        for (Consumer<StubLab> scenario : scenarios) {
            try (StubLab own = StubLab.start()) {
                scenario.accept(own);
                assertThatThrownBy(() -> LabPreflight.check(config(own.baseUrl())))
                        .isInstanceOf(LabPreflightException.class)
                        .hasMessageStartingWith(BANNER)
                        .hasMessageNotContaining(KEY)
                        .hasMessageNotContaining(bodyMarker)
                        .hasMessageNotContaining(headerMarker)
                        .hasMessageNotContaining("Authorization");
            }
        }
    }

    // ---- interruption ----

    @Test
    // @regression
    @DisplayName("An interrupted pre-flight fails with the banner and leaves the thread's interrupt flag set")
    void interruptionFailsClearlyAndRestoresTheInterruptFlag() {
        healthy();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> LabPreflight.check(config(lab.baseUrl())))
                    .isInstanceOf(LabPreflightException.class)
                    .hasMessageStartingWith(BANNER)
                    .hasMessageContaining("interrupted")
                    .hasMessageContaining(lab.baseUrl())
                    .hasMessageNotContaining(KEY);
            assertThat(Thread.currentThread().isInterrupted()).as("interrupt flag restored").isTrue();
        } finally {
            Thread.interrupted(); // never leak the flag into the next test
        }
    }
}
