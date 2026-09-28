package com.labflow.ui.tests.support;

import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;

import java.util.concurrent.TimeoutException;

/**
 * Copied from {@code api-tests}' {@code com.labflow.tests.support.ScenarioNameExtension} (about
 * 30 lines), not moved: moving it into {@code common/src/main} would put a JUnit dependency onto
 * production code, and this module has no other shared module to place it in yet. The planned
 * {@code test-data} module (a later Phase 2 spec) is where shared test helpers get consolidated.
 *
 * <p>Puts the business scenario at the top of every failed check: a failure whose message is
 * {@code expected: "Accessioned" but was: "Ready for Processing"} is rethrown as
 * {@code [Scenario: A registered sample appears in the list as Accessioned] expected: ...}.
 *
 * <p><b>Difference from the {@code api-tests} copy:</b> a screen check can also fail with a
 * {@link PlaywrightException} that is not an assertion at all — for example a {@code
 * TimeoutError} because a Page Object's wait for an element (say {@code #sample-details}) never
 * resolved, because the expected screen never appeared. An API check has no such failure mode,
 * so the original only ever touches {@link AssertionError}s. Here, a {@link PlaywrightException}
 * gets the same {@code [Scenario: ...]} prefix, so a check that fails before ever reaching an
 * assertion still names its scenario (FS 2.9 ¶1).
 *
 * <p><b>Added for the per-check time limit (spec 004, Slice 8):</b> when a check runs past
 * {@code junit.jupiter.execution.timeout.default} (see {@code junit-platform.properties}),
 * JUnit's own {@code TimeoutExtension} throws a plain {@link TimeoutException} — it has no
 * scenario of its own to attach, and unlike an {@link AssertionError} it is not something this
 * class can safely re-wrap as the same type (there is no "scenario-named timeout" type in
 * {@code java.util.concurrent}), so it is wrapped as a {@link ScenarioTimeoutException} instead,
 * with the original kept as its cause. Everything else is rethrown unchanged.
 *
 * <p>Registered on {@link UiTest} with {@code @ExtendWith}.
 */
public class ScenarioNameExtension implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (throwable instanceof AssertionError) {
            throw withScenario(context, throwable);
        }
        if (throwable instanceof PlaywrightException) {
            throw new PlaywrightException(scenarioMessage(context, throwable), throwable);
        }
        if (throwable instanceof TimeoutException) {
            throw new ScenarioTimeoutException(scenarioMessage(context, throwable), throwable);
        }
        throw throwable;
    }

    /**
     * A {@link TimeoutException} carries no scenario name of its own; this wraps one with the
     * same {@code [Scenario: ...]} prefix the other failure types get, keeping the original
     * {@link TimeoutException} as its cause so the real elapsed-time message is still visible.
     */
    public static final class ScenarioTimeoutException extends RuntimeException {
        ScenarioTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Wraps an {@link AssertionError} with the scenario prefix, keeping it as an
     * {@link AssertionFailedError} and preserving expected/actual (when present) so an IDE can
     * still show the comparison. The original is kept as the cause either way.
     */
    private static AssertionError withScenario(ExtensionContext context, Throwable throwable) {
        String message = scenarioMessage(context, throwable);
        if (throwable instanceof AssertionFailedError original
                && original.isExpectedDefined() && original.isActualDefined()) {
            return new AssertionFailedError(message, original.getExpected(), original.getActual(), original);
        }
        return new AssertionFailedError(message, throwable);
    }

    private static String scenarioMessage(ExtensionContext context, Throwable throwable) {
        return "[Scenario: " + context.getDisplayName() + "] " + throwable.getMessage();
    }
}
