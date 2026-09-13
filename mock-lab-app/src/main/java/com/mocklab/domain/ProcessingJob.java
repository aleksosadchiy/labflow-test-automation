package com.mocklab.domain;

import java.time.Instant;
import java.util.List;

/**
 * Owns the actual state-machine value for a {@link Sample} (1—1). Holds an
 * ordered {@code history} of every status the job has passed through, oldest
 * first. Transition/advance logic is a later slice — this record just holds
 * the shape.
 */
public record ProcessingJob(
        String id,
        String sampleId,
        SampleStatus currentStatus,
        List<StatusChange> history) {

    /**
     * One entry in a {@link ProcessingJob}'s history: the status reached and
     * when it was reached.
     */
    public record StatusChange(SampleStatus status, Instant timestamp) {
    }
}
