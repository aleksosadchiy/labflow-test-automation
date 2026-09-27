package com.labflow.ui.tests.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;

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
 * <p>Only assertion failures are touched (AssertJ failures are {@link AssertionError}s).
 * Anything else, such as a navigation error, is a different kind of problem and is rethrown
 * unchanged. The original failure stays as the cause, and its expected and actual values, when
 * it has them, are kept so an IDE can still show the comparison.
 *
 * <p>Registered on {@link UiTest} with {@code @ExtendWith}.
 */
public class ScenarioNameExtension implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (!(throwable instanceof AssertionError)) {
            throw throwable;
        }
        String message = "[Scenario: " + context.getDisplayName() + "] " + throwable.getMessage();
        if (throwable instanceof AssertionFailedError original
                && original.isExpectedDefined() && original.isActualDefined()) {
            throw new AssertionFailedError(message, original.getExpected(), original.getActual(), original);
        }
        throw new AssertionFailedError(message, throwable);
    }
}
