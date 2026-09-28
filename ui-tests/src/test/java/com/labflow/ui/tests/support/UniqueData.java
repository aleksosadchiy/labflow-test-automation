package com.labflow.ui.tests.support;

import java.util.UUID;

/**
 * Copied from {@code api-tests}' {@code com.labflow.tests.support.UniqueData}, not moved: moving
 * it into {@code common/src/main} would put a JUnit-test concern onto production code, and this
 * module has no other shared module to place it in yet. The planned {@code test-data} module (a
 * later Phase 2 spec) is where shared test helpers get consolidated.
 *
 * <p>Unique test data, so every screen check creates its own patient and samples and never
 * collides with another check or an earlier run against the same lab. Pure: no static mutable
 * state.
 */
public final class UniqueData {

    private UniqueData() {
    }

    /** A name that is unique across checks and runs, e.g. {@code ui-patient-3f2a...}. */
    public static String name(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
