package com.mocklab.api.error;

/**
 * Every user-facing message the lab can return, in one place (functional-spec.md
 * error tables; technical-considerations.md §2.3).
 *
 * <p>Keeping the literals here means a message is defined exactly once, and the
 * tests that assert on the exact text will fail loudly if one is edited by
 * accident. The last three (unsupported media type, not acceptable, internal
 * server error) are not in the functional spec; they exist so those cases still
 * return the standard error body.
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

    // Not in the functional spec; keep the standard error body for these cases too
    public static final String UNSUPPORTED_MEDIA_TYPE = "Unsupported media type.";
    public static final String NOT_ACCEPTABLE = "Not acceptable.";
    public static final String INTERNAL_SERVER_ERROR = "Internal server error.";

    private LabMessages() {
    }

    /**
     * Message for a rejected status change, e.g.
     * {@code invalidTransition("Processing", "Result Ready")} gives
     * "Invalid status transition: cannot move from Processing to Result Ready."
     * The caller supplies the human-readable stage names.
     */
    public static String invalidTransition(String fromDisplayName, String toDisplayName) {
        return "Invalid status transition: cannot move from " + fromDisplayName + " to " + toDisplayName + ".";
    }
}
