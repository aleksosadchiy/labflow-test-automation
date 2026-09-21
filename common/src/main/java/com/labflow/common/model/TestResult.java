package com.labflow.common.model;

/**
 * The test result of a sample, as the lab answers {@code GET /api/samples/{id}/result}
 * (available from the Result Ready stage on).
 *
 * @param sampleId    the sample the result belongs to
 * @param testName    the name of the test that was run
 * @param resultValue the result the lab produced
 */
public record TestResult(String sampleId, String testName, String resultValue) {
}
