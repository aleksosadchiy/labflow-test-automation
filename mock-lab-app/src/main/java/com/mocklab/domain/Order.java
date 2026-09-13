package com.mocklab.domain;

import java.time.Instant;

/**
 * An order for lab work on a {@link Patient}. Not surfaced on any screen —
 * auto-created behind the scenes whenever a {@link Sample} is registered.
 */
public record Order(String id, String patientId, Instant createdAt) {
}
