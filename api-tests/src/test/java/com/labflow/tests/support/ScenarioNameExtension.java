package com.labflow.tests.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;

/**
 * Puts the business scenario at the top of every failed check: a failure whose message is
 * {@code POST /api/patients expected 201 ...} is rethrown as
 * {@code [Scenario: A registered patient can be looked up ...] POST /api/patients expected 201 ...}.
 *
 * <p>Only assertion failures are touched (AssertJ failures and the framework's
 * {@code UnexpectedResponseError} are both {@link AssertionError}s). Anything else, such as a
 * connection error, is a different kind of problem and is rethrown unchanged. The original
 * failure stays as the cause, and its expected and actual values, when it has them, are kept so
 * an IDE can still show the comparison.
 *
 * <p>Registered once on {@link LabTest} with {@code @ExtendWith}.
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
