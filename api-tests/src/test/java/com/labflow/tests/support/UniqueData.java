package com.labflow.tests.support;

import java.util.UUID;

/**
 * Unique test data, so every check creates its own records and never collides with another
 * check or an earlier run against the same lab. Pure: no static mutable state.
 */
public final class UniqueData {

    private UniqueData() {
    }

    /** A name that is unique across checks and runs, e.g. {@code patient-3f2a...}. */
    public static String name(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
