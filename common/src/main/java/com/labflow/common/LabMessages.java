package com.labflow.common;

import com.labflow.common.model.WorkflowStage;

/**
 * The lab's exact user-facing messages, copied from the lab's own message list, so a check
 * can say "the lab refused with <em>this</em> message" without retyping the wording.
 *
 * <p>The wording is the lab's contract: if the lab changes a message, this is the single
 * place to update, and {@code LabMessagesTest} pins every literal so an accidental edit here
 * fails loudly. The constant names match the lab's.
 */
public final class LabMessages {

    // 400 - missing or invalid input
    public static final String PATIENT_NAME_REQUIRED = "Patient name is required.";
    public static final String PATIENT_REQUIRED = "Patient is required.";
    public static final String SAMPLE_TYPE_REQUIRED = "Sample type is required.";
    public static final String INVALID_SAMPLE_TYPE = "Invalid sample type.";
    public static final String STATUS_REQUIRED = "Status is required.";
    public static final String INVALID_STATUS = "Invalid status.";
    public static final String MALFORMED_REQUEST_BODY = "Malformed request body.";

    // 401
    public static final String UNAUTHORIZED = "Unauthorized.";

    // 404
    public static final String PATIENT_NOT_FOUND = "Patient not found.";
    public static final String ORDER_NOT_FOUND = "Order not found.";
    public static final String SAMPLE_NOT_FOUND = "Sample not found.";
    public static final String RESOURCE_NOT_FOUND = "Resource not found.";

    // 405
    public static final String METHOD_NOT_ALLOWED = "Method not allowed.";

    // 409 - business-rule conflicts and "not ready yet"
    public static final String ORDER_PATIENT_MISMATCH = "Order does not belong to this patient.";
    public static final String DUPLICATE_SAMPLE_TYPE = "A sample of this type already exists for this order.";
    public static final String RESULT_NOT_READY = "Test result is not ready yet.";
    public static final String REPORT_NOT_READY = "Report is not ready yet.";

    private LabMessages() {
    }

    /**
     * The message for a refused status change. It names the stages the way users see them,
     * for example {@code invalidTransition(PROCESSING, RESULT_READY)} gives
     * "Invalid status transition: cannot move from Processing to Result Ready."
     */
    public static String invalidTransition(WorkflowStage from, WorkflowStage to) {
        return "Invalid status transition: cannot move from " + from.displayName()
                + " to " + to.displayName() + ".";
    }
}
