package com.labflow.common;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the exact wording of every message in {@link LabMessages} (functional-spec.md §2.6:
 * a refusal is checked against the lab's own message). The expected text is typed out here on
 * purpose, character for character, so an accidental edit of a constant fails this test
 * instead of silently changing what every rejection check expects. Plain JUnit assertions,
 * because {@code common} has no other test dependency.
 *
 * @spec: 003-test-automation-foundation
 */
class LabMessagesTest {

    @Test
    // @regression
    @DisplayName("The 400 messages (missing or invalid input) have the lab's exact wording")
    void invalidInputMessagesAreExact() {
        assertEquals("Patient name is required.", LabMessages.PATIENT_NAME_REQUIRED);
        assertEquals("Patient is required.", LabMessages.PATIENT_REQUIRED);
        assertEquals("Sample type is required.", LabMessages.SAMPLE_TYPE_REQUIRED);
        assertEquals("Invalid sample type.", LabMessages.INVALID_SAMPLE_TYPE);
        assertEquals("Status is required.", LabMessages.STATUS_REQUIRED);
        assertEquals("Invalid status.", LabMessages.INVALID_STATUS);
        assertEquals("Malformed request body.", LabMessages.MALFORMED_REQUEST_BODY);
    }

    @Test
    // @regression
    @DisplayName("The 401 message has the lab's exact wording")
    void unauthorizedMessageIsExact() {
        assertEquals("Unauthorized.", LabMessages.UNAUTHORIZED);
    }

    @Test
    // @regression
    @DisplayName("The 404 messages have the lab's exact wording")
    void notFoundMessagesAreExact() {
        assertEquals("Patient not found.", LabMessages.PATIENT_NOT_FOUND);
        assertEquals("Order not found.", LabMessages.ORDER_NOT_FOUND);
        assertEquals("Sample not found.", LabMessages.SAMPLE_NOT_FOUND);
        assertEquals("Resource not found.", LabMessages.RESOURCE_NOT_FOUND);
    }

    @Test
    // @regression
    @DisplayName("The 405 message has the lab's exact wording")
    void methodNotAllowedMessageIsExact() {
        assertEquals("Method not allowed.", LabMessages.METHOD_NOT_ALLOWED);
    }

    @Test
    // @regression
    @DisplayName("The 409 messages (conflicts and 'not ready yet') have the lab's exact wording")
    void conflictMessagesAreExact() {
        assertEquals("Order does not belong to this patient.", LabMessages.ORDER_PATIENT_MISMATCH);
        assertEquals("A sample of this type already exists for this order.", LabMessages.DUPLICATE_SAMPLE_TYPE);
        assertEquals("Test result is not ready yet.", LabMessages.RESULT_NOT_READY);
        assertEquals("Report is not ready yet.", LabMessages.REPORT_NOT_READY);
    }

    @Test
    // @regression
    @DisplayName("An invalid transition names both stages the way users see them")
    void invalidTransitionNamesBothStages() {
        assertEquals("Invalid status transition: cannot move from Processing to Result Ready.",
                LabMessages.invalidTransition(WorkflowStage.PROCESSING, WorkflowStage.RESULT_READY));
        assertEquals("Invalid status transition: cannot move from Report Generated to Accessioned.",
                LabMessages.invalidTransition(WorkflowStage.REPORT_GENERATED, WorkflowStage.ACCESSIONED));
    }

    @Test
    // @regression
    @DisplayName("An invalid transition uses display names (with spaces), not the wire constant names")
    void invalidTransitionUsesDisplayNames() {
        assertEquals("Invalid status transition: cannot move from Ready for Processing to Quality Check.",
                LabMessages.invalidTransition(WorkflowStage.READY_FOR_PROCESSING, WorkflowStage.QUALITY_CHECK));
    }
}
