package com.mocklab.domain;

import java.time.Instant;

/**
 * The lab result for a {@link Sample}, auto-created the moment its
 * {@link ProcessingJob} transitions into {@link SampleStatus#RESULT_READY}
 * (see {@code ProcessingJobService#advance(String)} /
 * {@code ReportService}). Per technical-considerations.md §6, values are a
 * fixed/deterministic placeholder — no real bioinformatics, per the
 * project's non-goals.
 */
public record TestResult(
        String id,
        String sampleId,
        String testName,
        String resultValue,
        Instant generatedAt) {
}
