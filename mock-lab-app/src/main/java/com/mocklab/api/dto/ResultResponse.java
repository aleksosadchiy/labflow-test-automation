package com.mocklab.api.dto;

import com.mocklab.service.ResultView;

/** A sample's test result as returned by the API. */
public record ResultResponse(String sampleId, String testName, String resultValue) {

    public static ResultResponse from(ResultView view) {
        return new ResultResponse(view.sampleId(), view.testName(), view.resultValue());
    }
}
