package com.mocklab.exception;

/**
 * Thrown when a lab business rule rejects a request. Carries the
 * {@link FailureKind} (which picks the HTTP status) and a user-facing message
 * taken from {@code LabMessages}, safe to return verbatim.
 */
public class LabRuleException extends RuntimeException {

    private final FailureKind kind;

    public LabRuleException(FailureKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public FailureKind kind() {
        return kind;
    }
}
