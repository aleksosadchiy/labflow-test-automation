package com.labflow.client.http;

/**
 * Thrown by the "happy" client methods when the lab answers something other than what the
 * caller expected, for example {@code POST /api/patients expected 201 but the lab answered
 * 409 "..."}. It is an {@link AssertionError} so a test that hits it fails (rather than
 * errors) and the message is what the reader sees. The message never contains headers,
 * request bodies or names.
 */
public final class UnexpectedResponseError extends AssertionError {

    private static final long serialVersionUID = 1L;

    public UnexpectedResponseError(String message) {
        super(message);
    }
}
