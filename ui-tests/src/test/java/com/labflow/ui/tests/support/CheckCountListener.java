package com.labflow.ui.tests.support;

import com.labflow.common.junit.Labels;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs how many lab checks a run executed, as one INFO line at the end of the run:
 * {@code Lab checks run: N (labels: <groups or "all">)}.
 *
 * <p>Why: asking for a label that no check carries (for example {@code -Dgroups=nosuch}) is not an
 * error, so Maven just says {@code Tests run: 0} and the build is green. This line makes the empty
 * selection unmistakable. A "check" is a test tagged {@code api} or {@code ui}; the framework's own
 * self-checks are untagged and are not counted.
 *
 * <p>The labels are read from the {@code groups} system property. Surefire copies the Maven
 * command-line properties ({@code -Dgroups=...}) into the forked test JVM, so the property is
 * there; it is only absent when the labels were set some other way (for example in a POM), in
 * which case the text says {@code all}.
 *
 * <p>Registered by {@code ServiceLoader}
 * ({@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}). Copied between
 * {@code api-tests} and {@code ui-tests}, like the pre-flight listener: {@code common} has no
 * JUnit launcher dependency, and each module registers listeners on its own classpath.
 */
public class CheckCountListener implements TestExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CheckCountListener.class);

    private final AtomicInteger checksRun = new AtomicInteger();

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (test.isTest() && isLabCheck(test)) {
            checksRun.incrementAndGet();
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOG.info(summary(checksRun.get(), System.getProperty("groups")));
    }

    private static boolean isLabCheck(TestIdentifier test) {
        return test.getTags().stream()
                .anyMatch(tag -> tag.getName().equals(Labels.API) || tag.getName().equals(Labels.UI));
    }

    /** The one-line summary; blank labels mean no label was asked for, so every check ran. */
    static String summary(int checksRun, String groups) {
        String labels = groups == null || groups.isBlank() ? "all" : groups.trim();
        return "Lab checks run: " + checksRun + " (labels: " + labels + ")";
    }
}
