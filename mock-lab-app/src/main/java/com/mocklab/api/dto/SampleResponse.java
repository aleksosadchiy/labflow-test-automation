package com.mocklab.api.dto;

import com.mocklab.service.SampleView;

/**
 * A sample as returned by the API. Enum values are sent as their names
 * ({@code BLOOD}, {@code ACCESSIONED}) and {@code createdAt} as an ISO-8601 instant.
 */
public record SampleResponse(
        String id,
        String patientId,
        String orderId,
        String sampleType,
        String status,
        String createdAt) {

    public static SampleResponse from(SampleView view) {
        return new SampleResponse(
                view.id(),
                view.patientId(),
                view.orderId(),
                view.sampleType().name(),
                view.status().name(),
                view.createdAt().toString());
    }
}
