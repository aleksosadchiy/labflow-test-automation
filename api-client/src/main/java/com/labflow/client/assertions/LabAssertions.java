package com.labflow.client.assertions;

import com.labflow.client.http.LabResponse;

/**
 * Entry point for the lab-specific AssertJ assertions:
 * <pre>{@code
 * LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, LabMessages.DUPLICATE_SAMPLE_TYPE);
 * }</pre>
 * Refer to it by class name (or import it statically only where AssertJ's own
 * {@code assertThat} is not also imported) so the two never get mixed up.
 */
public final class LabAssertions {

    private LabAssertions() {
    }

    /** Starts an assertion on one answer from the lab. */
    public static LabResponseAssert assertThat(LabResponse<?> actual) {
        return new LabResponseAssert(actual);
    }
}
