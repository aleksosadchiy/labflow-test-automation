package com.mocklab.exception;

/**
 * The category of a business-rule failure. It decides the HTTP status the API
 * answers with: {@code VALIDATION} is 400, {@code NOT_FOUND} is 404 and
 * {@code CONFLICT} is 409.
 */
public enum FailureKind {
    VALIDATION,
    NOT_FOUND,
    CONFLICT
}
