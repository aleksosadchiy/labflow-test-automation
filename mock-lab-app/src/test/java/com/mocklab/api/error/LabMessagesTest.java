package com.mocklab.api.error;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the exact user-facing text. Later API tests assert on these literals, so
 * an accidental edit to a constant must fail here first.
 */
class LabMessagesTest {

    // @regression
    @Test
    void messagesMatchTheFunctionalSpecExactly() {
        assertThat(LabMessages.PATIENT_NAME_REQUIRED).isEqualTo("Patient name is required.");
        assertThat(LabMessages.PATIENT_REQUIRED).isEqualTo("Patient is required.");
        assertThat(LabMessages.SAMPLE_TYPE_REQUIRED).isEqualTo("Sample type is required.");
        assertThat(LabMessages.INVALID_SAMPLE_TYPE).isEqualTo("Invalid sample type.");
        assertThat(LabMessages.STATUS_REQUIRED).isEqualTo("Status is required.");
        assertThat(LabMessages.INVALID_STATUS).isEqualTo("Invalid status.");
        assertThat(LabMessages.MALFORMED_REQUEST_BODY).isEqualTo("Malformed request body.");
        assertThat(LabMessages.UNAUTHORIZED).isEqualTo("Unauthorized.");
        assertThat(LabMessages.PATIENT_NOT_FOUND).isEqualTo("Patient not found.");
        assertThat(LabMessages.ORDER_NOT_FOUND).isEqualTo("Order not found.");
        assertThat(LabMessages.SAMPLE_NOT_FOUND).isEqualTo("Sample not found.");
        assertThat(LabMessages.RESOURCE_NOT_FOUND).isEqualTo("Resource not found.");
        assertThat(LabMessages.METHOD_NOT_ALLOWED).isEqualTo("Method not allowed.");
        assertThat(LabMessages.ORDER_PATIENT_MISMATCH).isEqualTo("Order does not belong to this patient.");
        assertThat(LabMessages.DUPLICATE_SAMPLE_TYPE)
                .isEqualTo("A sample of this type already exists for this order.");
        assertThat(LabMessages.RESULT_NOT_READY).isEqualTo("Test result is not ready yet.");
        assertThat(LabMessages.REPORT_NOT_READY).isEqualTo("Report is not ready yet.");
    }

    // @regression
    @Test
    void additionsBeyondTheFunctionalSpecMatchTechSpec() {
        assertThat(LabMessages.UNSUPPORTED_MEDIA_TYPE).isEqualTo("Unsupported media type.");
        assertThat(LabMessages.NOT_ACCEPTABLE).isEqualTo("Not acceptable.");
        assertThat(LabMessages.INTERNAL_SERVER_ERROR).isEqualTo("Internal server error.");
    }

    // @regression
    @Test
    void invalidTransitionNamesBothStages() {
        assertThat(LabMessages.invalidTransition("Processing", "Result Ready"))
                .isEqualTo("Invalid status transition: cannot move from Processing to Result Ready.");
    }
}
