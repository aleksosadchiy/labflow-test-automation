package com.labflow.testdata;

import java.util.UUID;

/**
 * The single generator of unique test values, so every check creates its own records and never
 * collides with another check, a parallel run or an earlier run against the same lab. Every value
 * starts with {@link #TEST_PREFIX}, so it is obviously made up by the framework and can never be
 * mistaken for a real person. Pure: no static mutable state.
 */
public final class UniqueData {

    /** The fixed prefix that marks every generated value as framework-made. */
    public static final String TEST_PREFIX = "TEST-";

    private UniqueData() {
    }

    /** A value unique across checks and runs, e.g. {@code TEST-patient-3f2a...}. */
    public static String name(String kind) {
        return TEST_PREFIX + kind + "-" + UUID.randomUUID();
    }
}
