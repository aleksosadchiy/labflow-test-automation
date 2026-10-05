package com.labflow.tests.support;

// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression

import com.labflow.common.config.LabConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the run banner of {@link LabPreflightListener} (spec 005 tech spec §2.7): it
 * names the environment and the address, hides the key, and an unknown environment is rejected
 * while the settings are resolved, before any banner or probe. No lab is needed.
 */
class LabPreflightListenerTest {

    private static final String SECRET = "super-secret-key-123";

    @Test
    @DisplayName("The banner names the environment and the lab address and shows the key as ***")
    void bannerShowsEnvironmentAndAddress() {
        LabConfig config = LabConfig.load(
                Map.<String, String>of("environment", "ci", "baseUrl", "http://localhost:18080")::get, Map.<String, String>of()::get);

        assertThat(LabPreflightListener.banner(config))
                .isEqualTo("Lab run: environment=ci address=http://localhost:18080 key=***");
    }

    @Test
    @DisplayName("The banner never contains the real access key")
    void bannerNeverContainsTheKey() {
        LabConfig config = LabConfig.load(Map.<String, String>of()::get, Map.<String, String>of("API_TOKEN", SECRET)::get);

        assertThat(LabPreflightListener.banner(config)).contains("key=***").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("An unknown environment stops the run while the settings are resolved, before any banner")
    void unknownEnvironmentIsRejectedBeforeTheBanner() {
        assertThatThrownBy(() -> LabConfig.load(Map.<String, String>of("environment", "nope")::get, Map.<String, String>of()::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown environment 'nope'. Known: local, ci");
    }

    @Test
    @DisplayName("A second (nested) launcher session does not announce or probe again")
    void onlyTheFirstSessionRunsTheAction() {
        AtomicBoolean guard = new AtomicBoolean();
        AtomicInteger runs = new AtomicInteger();

        LabPreflightListener.runOnce(guard, runs::incrementAndGet);
        LabPreflightListener.runOnce(guard, runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }
}
