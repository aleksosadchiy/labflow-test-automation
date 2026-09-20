package com.mocklab.service;

import com.mocklab.domain.SampleStatus;

import java.time.Instant;
import java.util.List;

/**
 * A sample's progress through the lab workflow, returned by
 * {@link SampleQueryService#progress(String)}.
 *
 * <p>{@code stages} lists every stage the sample has reached, oldest first and
 * including the current one, so {@code currentStatus} equals the last entry's status.
 */
public record ProgressView(String sampleId, SampleStatus currentStatus, List<StageEntry> stages) {

    /** One stage the sample has reached, and when it got there. */
    public record StageEntry(SampleStatus status, Instant reachedAt) {
    }
}
