package com.mocklab.api.dto;

/**
 * Body of {@code POST /api/samples}. Only {@code patientId}, {@code sampleType}
 * and the optional {@code orderId} are read; any other field a client sends (for
 * example an {@code id} or a {@code status}) is ignored, because the lab assigns
 * IDs and every new sample starts at {@code ACCESSIONED}.
 *
 * <p>A missing, {@code null} or blank {@code orderId} means "no order named":
 * the lab creates a new order for the sample.
 */
public record SampleRequest(String patientId, String sampleType, String orderId) {
}
