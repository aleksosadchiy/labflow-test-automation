package com.mocklab.service;

/** A sample's test result, returned by {@link SampleQueryService#result(String)}. */
public record ResultView(String sampleId, String testName, String resultValue) {
}
