package com.labflow.common.model;

import java.time.Instant;

/**
 * The report of a sample, as the lab answers {@code GET /api/samples/{id}/report} (available
 * at the Report Generated stage).
 *
 * @param sampleId    the sample the report belongs to
 * @param testName    the name of the test that was run
 * @param resultValue the result the lab produced
 * @param generatedAt when the lab generated the report
 */
public record Report(String sampleId, String testName, String resultValue, Instant generatedAt) {
}
