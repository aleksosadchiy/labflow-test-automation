package com.mocklab.domain;

/**
 * The lab workflow's state-machine stages, in declaration order matching the
 * order a {@link Sample} moves through them. The actual transition logic
 * lives in {@code ProcessingJobService} (a later slice) — this enum only
 * defines the stages themselves.
 */
public enum SampleStatus {
    ACCESSIONED,
    READY_FOR_PROCESSING,
    PROCESSING,
    SEQUENCING,
    BIOINFORMATICS,
    QUALITY_CHECK,
    RESULT_READY,
    REPORT_GENERATED
}
