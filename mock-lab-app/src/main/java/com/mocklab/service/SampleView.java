package com.mocklab.service;

import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;

import java.time.Instant;

/**
 * A sample as the API reads it, returned by {@link SampleQueryService}. Its
 * {@code status} comes from the sample's processing job (the source of truth),
 * not from the sample's own stored mirror.
 */
public record SampleView(
        String id,
        String patientId,
        String orderId,
        SampleType sampleType,
        SampleStatus status,
        Instant createdAt) {
}
