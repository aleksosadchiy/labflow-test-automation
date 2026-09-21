package com.labflow.common.model;

// @layer: unit
// @spec: 003-test-automation-foundation

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WorkflowStage}, per functional-spec.md §2.4: all eight
 * stages are available in their proper order, and "what follows Processing?"
 * answers Sequencing while "what follows Report Generated?" answers "nothing".
 * Plain JUnit assertions, because {@code common} has no other test dependency.
 *
 * @spec: 003-test-automation-foundation
 */
class WorkflowStageTest {

    @Test
    // @regression
    @DisplayName("There are exactly eight stages")
    void thereAreExactlyEightStages() {
        assertEquals(8, WorkflowStage.values().length);
    }

    @Test
    // @regression
    @DisplayName("Declaration order is the workflow order, and constant names are the lab's wire names")
    void declarationOrderIsTheWorkflowOrder() {
        List<String> names = Arrays.stream(WorkflowStage.values()).map(WorkflowStage::name).toList();

        assertEquals(List.of(
                "ACCESSIONED",
                "READY_FOR_PROCESSING",
                "PROCESSING",
                "SEQUENCING",
                "BIOINFORMATICS",
                "QUALITY_CHECK",
                "RESULT_READY",
                "REPORT_GENERATED"), names);
    }

    @Test
    // @regression
    @DisplayName("Every stage has the exact human-readable name users see")
    void displayNamesAreExact() {
        assertEquals("Accessioned", WorkflowStage.ACCESSIONED.displayName());
        assertEquals("Ready for Processing", WorkflowStage.READY_FOR_PROCESSING.displayName());
        assertEquals("Processing", WorkflowStage.PROCESSING.displayName());
        assertEquals("Sequencing", WorkflowStage.SEQUENCING.displayName());
        assertEquals("Bioinformatics", WorkflowStage.BIOINFORMATICS.displayName());
        assertEquals("Quality Check", WorkflowStage.QUALITY_CHECK.displayName());
        assertEquals("Result Ready", WorkflowStage.RESULT_READY.displayName());
        assertEquals("Report Generated", WorkflowStage.REPORT_GENERATED.displayName());
    }

    @Test
    // @regression
    @DisplayName("The stage after Processing is Sequencing")
    void stageAfterProcessingIsSequencing() {
        assertEquals(Optional.of(WorkflowStage.SEQUENCING), WorkflowStage.PROCESSING.next());
    }

    @Test
    // @regression
    @DisplayName("There is no stage after Report Generated")
    void thereIsNoStageAfterReportGenerated() {
        assertTrue(WorkflowStage.REPORT_GENERATED.next().isEmpty());
    }

    @Test
    // @regression
    @DisplayName("Every stage except the last is followed by the next constant in declaration order")
    void everyStageIsFollowedByTheNextConstant() {
        WorkflowStage[] stages = WorkflowStage.values();

        for (int i = 0; i < stages.length - 1; i++) {
            assertEquals(Optional.of(stages[i + 1]), stages[i].next(),
                    "stage after " + stages[i].displayName());
        }
        assertTrue(stages[stages.length - 1].next().isEmpty(), "the last stage must have no successor");
    }
}
