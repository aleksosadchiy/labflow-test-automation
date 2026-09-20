package com.mocklab.api.dto;

import com.mocklab.service.ReportView;

/**
 * A sample's report as returned by the API: the same content the View Report
 * screen shows, with {@code generatedAt} as an ISO-8601 instant.
 */
public record ReportResponse(String sampleId, String testName, String resultValue, String generatedAt) {

    public static ReportResponse from(ReportView view) {
        return new ReportResponse(
                view.sampleId(), view.testName(), view.resultValue(), view.generatedAt().toString());
    }
}
