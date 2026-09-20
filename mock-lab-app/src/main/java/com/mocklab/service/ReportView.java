package com.mocklab.service;

import java.time.Instant;

/**
 * A sample's report, returned by {@link SampleQueryService#report(String)}. It carries
 * the same content the View Report screen shows: the test name and result value from
 * the stored test result, and the time the report was generated.
 */
public record ReportView(String sampleId, String testName, String resultValue, Instant generatedAt) {
}
