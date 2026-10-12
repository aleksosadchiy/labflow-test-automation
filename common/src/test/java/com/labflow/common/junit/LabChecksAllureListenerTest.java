package com.labflow.common.junit;

// @layer: unit
// @spec: 007-allure-report
// @regression

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LabChecksAllureListener} per spec 007 technical-considerations.md §2.2,
 * with a fake in place of Allure's listener: only tagged identifiers reach it, for all four
 * per-identifier events, and the start and end of the run always do.
 */
class LabChecksAllureListenerTest {

    /** Records the name of every call it receives. */
    static class RecordingListener implements TestExecutionListener {
        final List<String> calls = new ArrayList<>();

        @Override
        public void testPlanExecutionStarted(org.junit.platform.launcher.TestPlan testPlan) {
            calls.add("planStarted");
        }

        @Override
        public void testPlanExecutionFinished(org.junit.platform.launcher.TestPlan testPlan) {
            calls.add("planFinished");
        }

        @Override
        public void executionStarted(TestIdentifier test) {
            calls.add("started");
        }

        @Override
        public void executionFinished(TestIdentifier test, TestExecutionResult result) {
            calls.add("finished");
        }

        @Override
        public void executionSkipped(TestIdentifier test, String reason) {
            calls.add("skipped");
        }

        @Override
        public void reportingEntryPublished(TestIdentifier test, ReportEntry entry) {
            calls.add("entry");
        }
    }

    private static TestIdentifier identifier(String... tags) {
        Set<TestTag> tagSet = new java.util.HashSet<>();
        for (String tag : tags) {
            tagSet.add(TestTag.create(tag));
        }
        TestDescriptor descriptor = new AbstractTestDescriptor(UniqueId.forEngine("fake"), "fake") {
            @Override
            public Type getType() {
                return Type.TEST;
            }

            @Override
            public Set<TestTag> getTags() {
                return tagSet;
            }
        };
        return TestIdentifier.from(descriptor);
    }

    private static List<String> forwardedFor(TestIdentifier id) {
        RecordingListener fake = new RecordingListener();
        LabChecksAllureListener listener = new LabChecksAllureListener(fake);
        listener.executionStarted(id);
        listener.executionFinished(id, TestExecutionResult.successful());
        listener.executionSkipped(id, "reason");
        listener.reportingEntryPublished(id, ReportEntry.from("key", "value"));
        return fake.calls;
    }

    @Test
    @DisplayName("An api-tagged identifier is forwarded for all four per-identifier events")
    void apiForwarded() {
        assertEquals(List.of("started", "finished", "skipped", "entry"), forwardedFor(identifier("api", "regression")));
    }

    @Test
    @DisplayName("A ui-tagged identifier is forwarded for all four per-identifier events")
    void uiForwarded() {
        assertEquals(List.of("started", "finished", "skipped", "entry"), forwardedFor(identifier("ui")));
    }

    @Test
    @DisplayName("An untagged identifier is forwarded for none of the events")
    void untaggedNotForwarded() {
        assertTrue(forwardedFor(identifier()).isEmpty());
    }

    @Test
    @DisplayName("An identifier with only other tags is forwarded for none of the events")
    void otherTagsNotForwarded() {
        assertTrue(forwardedFor(identifier("smoke", "regression")).isEmpty());
    }

    @Test
    @DisplayName("The start and the end of the run are always forwarded")
    void planAlwaysForwarded() {
        RecordingListener fake = new RecordingListener();
        LabChecksAllureListener listener = new LabChecksAllureListener(fake);
        listener.testPlanExecutionStarted(null);
        listener.testPlanExecutionFinished(null);
        assertEquals(List.of("planStarted", "planFinished"), fake.calls);
    }
}
