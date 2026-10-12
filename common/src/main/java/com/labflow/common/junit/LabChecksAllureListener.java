package com.labflow.common.junit;

import io.qameta.allure.junitplatform.AllureJunitPlatform;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Records only the lab checks in the Allure report: it passes JUnit's events on to Allure's own
 * listener, but only for tests and containers tagged {@code api} or {@code ui}.
 *
 * <p>Why: Allure's listener ({@link AllureJunitPlatform}) records every test in the module,
 * including the framework's own self-checks, and has no tag filter of its own. The report must
 * list the checks that test the lab and nothing else, so this class stands in front of it.
 *
 * <p>What is passed on:
 * <ul>
 *   <li>the start and the end of the run, always: Allure sets up and clears its per-thread state
 *       there;</li>
 *   <li>started, finished, skipped and report-entry events, only when {@link Labels#isLabCheck}
 *       accepts the identifier's tags.</li>
 * </ul>
 *
 * <p>The rule looks at the tags and not at "is this a test", because a failing {@code @BeforeAll}
 * or a skipped class is reported through its container (the class), which carries the same tags.
 * It is the rule {@link CheckCountListener} counts with, so the report holds as many checks as
 * the run says it executed.
 *
 * <p>For this to work a module must switch off Allure's own auto-registered listener (the JUnit
 * Platform parameter {@code junit.platform.execution.listeners.deactivate}) and register this
 * class through {@code ServiceLoader}
 * ({@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}), which is why it
 * has a public no-argument constructor.
 */
public class LabChecksAllureListener implements TestExecutionListener {

    private final TestExecutionListener allure;

    /** Used by {@code ServiceLoader}: forwards to a new Allure listener. */
    public LabChecksAllureListener() {
        this(new AllureJunitPlatform());
    }

    /** For unit tests: forwards to the given listener instead of Allure's. */
    LabChecksAllureListener(TestExecutionListener allure) {
        this.allure = allure;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        allure.testPlanExecutionStarted(testPlan);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        allure.testPlanExecutionFinished(testPlan);
    }

    @Override
    public void executionStarted(TestIdentifier test) {
        if (Labels.isLabCheck(test.getTags())) {
            allure.executionStarted(test);
        }
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (Labels.isLabCheck(test.getTags())) {
            allure.executionFinished(test, result);
        }
    }

    @Override
    public void executionSkipped(TestIdentifier test, String reason) {
        if (Labels.isLabCheck(test.getTags())) {
            allure.executionSkipped(test, reason);
        }
    }

    @Override
    public void reportingEntryPublished(TestIdentifier test, ReportEntry entry) {
        if (Labels.isLabCheck(test.getTags())) {
            allure.reportingEntryPublished(test, entry);
        }
    }
}
