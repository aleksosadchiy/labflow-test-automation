// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.support;

import com.microsoft.playwright.PlaywrightException;
import com.labflow.ui.tests.support.ScenarioNameExtension.ScenarioTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the extension directly, with a hand-built {@link ExtensionContext} that answers only
 * {@code getDisplayName()} (a {@link Proxy}; any other call fails loudly), the same technique
 * {@code api-tests}' {@code ScenarioNameExtensionTest} uses. No mocking library, no lab, no
 * browser: this is exactly what a screen check's failure looks like before it ever reaches the
 * console, so FS 2.9 ¶1 ("the output names the scenario and states what was expected and what
 * the screen actually showed") is proven directly instead of only by a one-off manual break/fix
 * during a Verify task.
 *
 * <p>Closes a gap the {@code api-tests} original does not have to cover: this copy also handles a
 * {@link PlaywrightException} (a Page Object's wait for the wrong screen timed out) and a
 * {@link TimeoutException} (the per-check time limit from Slice 8 fired), neither of which existed
 * when the original was written. Those two branches had no automated coverage at all before this
 * file - only the manual demonstrations in the Slice 6 and Slice 8 Verify tasks, whose evidence
 * was deliberately deleted afterwards.
 */
class ScenarioNameExtensionTest {

    private static final String SCENARIO = "A registered sample appears in the list as Accessioned";

    private final ScenarioNameExtension extension = new ScenarioNameExtension();

    private static ExtensionContext contextNamed(String displayName) {
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class<?>[]{ExtensionContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getDisplayName")) {
                        return displayName;
                    }
                    throw new UnsupportedOperationException("not needed by the extension: " + method.getName());
                });
    }

    @Test
    @DisplayName("A failed comparison gets the scenario name in front and keeps its expected and actual values")
    void failedComparisonGetsScenarioPrefixAndKeepsExpectedAndActual() {
        AssertionFailedError original = new AssertionFailedError("expected: \"Accessioned\" but was: \"Ready for Processing\"",
                "Accessioned", "Ready for Processing");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(AssertionFailedError.class, thrown -> {
                    assertThat(thrown.getMessage())
                            .isEqualTo("[Scenario: " + SCENARIO + "] expected: \"Accessioned\" but was: \"Ready for Processing\"");
                    assertThat(thrown.isExpectedDefined()).isTrue();
                    assertThat(thrown.isActualDefined()).isTrue();
                    assertThat(thrown.getExpected().getValue()).isEqualTo("Accessioned");
                    assertThat(thrown.getActual().getValue()).isEqualTo("Ready for Processing");
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("A plain assertion error gets the scenario name in front and is kept as the cause")
    void plainAssertionErrorGetsScenarioPrefix() {
        AssertionError original = new AssertionError("the Sign In screen's error message for a wrong password expected"
                + " \"Invalid username or password.\" but was \"\"");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(AssertionFailedError.class, thrown -> {
                    assertThat(thrown.getMessage()).startsWith("[Scenario: " + SCENARIO + "] ");
                    assertThat(thrown.isExpectedDefined()).isFalse();
                    assertThat(thrown.isActualDefined()).isFalse();
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("A Playwright failure - a Page Object's wait for the wrong screen - also gets the scenario name in front")
    void playwrightExceptionGetsScenarioPrefix() {
        PlaywrightException original = new PlaywrightException("Timeout 30000ms exceeded waiting for locator('#sample-details')");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(PlaywrightException.class, thrown -> {
                    assertThat(thrown.getMessage())
                            .isEqualTo("[Scenario: " + SCENARIO + "] "
                                    + "Timeout 30000ms exceeded waiting for locator('#sample-details')");
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("A check that runs past the per-check time limit is wrapped with the scenario name, keeping the original timeout as its cause")
    void timeoutExceptionIsWrappedWithScenarioPrefix() {
        TimeoutException original = new TimeoutException("test timed out after 2 minutes");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(ScenarioTimeoutException.class, thrown -> {
                    assertThat(thrown.getMessage())
                            .isEqualTo("[Scenario: " + SCENARIO + "] test timed out after 2 minutes");
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("An error that is none of the above, such as a connection error, is passed on unchanged")
    void unrelatedThrowableIsRethrownUnchanged() {
        IOException original = new IOException("Connection refused");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isSameAs(original)
                .hasMessage("Connection refused");
    }
}
