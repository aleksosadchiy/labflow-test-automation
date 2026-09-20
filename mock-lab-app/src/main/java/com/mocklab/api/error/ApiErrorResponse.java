package com.mocklab.api.error;

/**
 * The standard body of every API rejection. Tests assert on {@code status} and
 * {@code message}; {@code error} is the HTTP reason phrase, {@code path} is the
 * request URI and {@code timestamp} is an ISO-8601 instant.
 */
public record ApiErrorResponse(int status, String error, String message, String path, String timestamp) {
}
