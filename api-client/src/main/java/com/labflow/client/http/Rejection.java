package com.labflow.client.http;

/**
 * The kinds of refusal the lab gives, named the way the functional spec names them, each with
 * the HTTP status the lab uses for it. A check says "rejected as {@code CONFLICT}" instead of
 * repeating the number 409.
 */
public enum Rejection {

    /** The request is missing something or carries something invalid (400). */
    INVALID_INPUT(400),
    /** The access key is missing or wrong (401). */
    UNAUTHORIZED(401),
    /** The record or route does not exist (404). */
    NOT_FOUND(404),
    /** The route exists but does not support that HTTP method (405). */
    METHOD_NOT_ALLOWED(405),
    /** The request is well formed but breaks a business rule, or asks for something not ready (409). */
    CONFLICT(409);

    private final int status;

    Rejection(int status) {
        this.status = status;
    }

    /** The HTTP status code the lab answers with for this kind of refusal. */
    public int status() {
        return status;
    }
}
