package com.labflow.ui.tests.support;

// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression

import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.BrowserChoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the run banner of {@link UiPreflightListener} (spec 005 tech spec §2.7): same
 * line as the API run plus the browser, and the key is never shown. No lab or browser is needed.
 */
class UiPreflightListenerTest {

    private static final String SECRET = "super-secret-key-123";

    @Test
    @DisplayName("The banner names the environment, the lab address and the browser, and shows the key as ***")
    void bannerShowsEnvironmentAddressAndBrowser() {
        LabConfig config = LabConfig.load(Map.<String, String>of("environment", "ci")::get, Map.<String, String>of()::get);

        assertThat(UiPreflightListener.banner(config, BrowserChoice.WEBKIT))
                .isEqualTo("Lab run: environment=ci address=http://localhost:8080 key=*** browser=webkit");
    }

    @Test
    @DisplayName("The banner never contains the real access key")
    void bannerNeverContainsTheKey() {
        LabConfig config = LabConfig.load(Map.<String, String>of()::get, Map.<String, String>of("API_TOKEN", SECRET)::get);

        assertThat(UiPreflightListener.banner(config, BrowserChoice.CHROMIUM))
                .contains("key=***")
                .doesNotContain(SECRET);
    }

    @Test
    @DisplayName("A second (nested) launcher session does not announce or probe again")
    void onlyTheFirstSessionRunsTheAction() {
        AtomicBoolean guard = new AtomicBoolean();
        AtomicInteger runs = new AtomicInteger();

        UiPreflightListener.runOnce(guard, runs::incrementAndGet);
        UiPreflightListener.runOnce(guard, runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }
}
