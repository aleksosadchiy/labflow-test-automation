package com.mocklab.api.dto;

/**
 * Body of {@code POST /api/samples}. Only {@code patientId} and {@code sampleType}
 * are read; any other field a client sends (for example an {@code id} or a
 * {@code status}) is ignored, because the lab assigns IDs and every new sample
 * starts at {@code ACCESSIONED}.
 */
public record SampleRequest(String patientId, String sampleType) {
}
