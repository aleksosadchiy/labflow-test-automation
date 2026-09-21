// @layer: unit
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the extension directly, with a hand-built {@link ExtensionContext} that answers only
 * {@code getDisplayName()} (a {@link Proxy}; any other call fails loudly). No mocking library.
 */
class ScenarioNameExtensionTest {

    private static final String SCENARIO = "A registered patient can be looked up";

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
        AssertionFailedError original = new AssertionFailedError("expected 201 but was 409", "201", "409");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(AssertionFailedError.class, thrown -> {
                    assertThat(thrown.getMessage()).isEqualTo("[Scenario: " + SCENARIO + "] expected 201 but was 409");
                    assertThat(thrown.isExpectedDefined()).isTrue();
                    assertThat(thrown.isActualDefined()).isTrue();
                    assertThat(thrown.getExpected().getValue()).isEqualTo("201");
                    assertThat(thrown.getActual().getValue()).isEqualTo("409");
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("A plain assertion error gets the scenario name in front and is kept as the cause")
    void plainAssertionErrorGetsScenarioPrefix() {
        AssertionError original = new AssertionError("POST /api/patients expected 201 but the lab answered 409");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isInstanceOfSatisfying(AssertionFailedError.class, thrown -> {
                    assertThat(thrown.getMessage()).startsWith("[Scenario: " + SCENARIO + "] ");
                    assertThat(thrown.isExpectedDefined()).isFalse();
                    assertThat(thrown.isActualDefined()).isFalse();
                    assertThat(thrown.getCause()).isSameAs(original);
                });
    }

    @Test
    @DisplayName("The original failure message is still there after the scenario name")
    void originalMessageIsStillInside() {
        String labMessage = "POST /api/samples expected 201 but the lab answered 400 \"Invalid sample type.\"";

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), new AssertionError(labMessage)))
                .hasMessage("[Scenario: " + SCENARIO + "] " + labMessage);
    }

    @Test
    @DisplayName("An error that is not an assertion failure, such as a connection error, is passed on unchanged")
    void nonAssertionThrowableIsRethrownUnchanged() {
        IOException original = new IOException("Connection refused");

        assertThatThrownBy(() -> extension.handleTestExecutionException(contextNamed(SCENARIO), original))
                .isSameAs(original)
                .hasMessage("Connection refused");
    }
}
