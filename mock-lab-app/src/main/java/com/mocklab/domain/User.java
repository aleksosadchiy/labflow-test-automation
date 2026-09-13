package com.mocklab.domain;

/**
 * Login credential for the UI only. Plain in-memory record, no persistence,
 * no password hashing — production-grade auth is an explicit non-goal for
 * this mock lab application.
 */
public record User(String username, String password) {
}
