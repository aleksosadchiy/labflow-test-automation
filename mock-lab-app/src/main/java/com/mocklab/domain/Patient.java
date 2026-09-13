package com.mocklab.domain;

/**
 * A lab patient. Plain in-memory record, no persistence. There is no
 * create-patient screen anywhere in scope, so patients must pre-exist —
 * {@link com.mocklab.config.DataSeeder} seeds a small fixed set on every
 * boot so both UI and API testers can reference known, stable IDs.
 */
public record Patient(String id, String name) {
}
