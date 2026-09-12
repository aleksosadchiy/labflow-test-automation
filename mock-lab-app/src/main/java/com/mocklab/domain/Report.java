package com.mocklab.domain;

import java.time.Instant;

/**
 * The report for a {@link Sample}, auto-created the moment its
 * {@link ProcessingJob} transitions into {@link SampleStatus#REPORT_GENERATED}
 * (see {@code ProcessingJobService#advance(String)} / {@code ReportService}).
 * Logically 1—1 with the sample's {@link TestResult}.
 */
public record Report(
        String id,
        String sampleId,
        Instant generatedAt) {
}
