package com.mocklab.api.dto;

import com.mocklab.domain.Order;
import com.mocklab.domain.Sample;
import com.mocklab.service.SampleCreationResult;

/**
 * The body of a successful {@code POST /api/samples}: the same flat fields as
 * {@link SampleResponse}, plus the {@code order} the sample was registered under.
 */
public record SampleCreatedResponse(
        String id,
        String patientId,
        String orderId,
        String sampleType,
        String status,
        String createdAt,
        OrderSummary order) {

    /** The few order fields a freshly registered sample exposes. */
    public record OrderSummary(String id, String patientId, String createdAt) {
    }

    public static SampleCreatedResponse from(SampleCreationResult.Success created) {
        Sample sample = created.sample();
        Order order = created.order();
        return new SampleCreatedResponse(
                sample.id(),
                sample.patientId(),
                sample.orderId(),
                sample.sampleType().name(),
                sample.status().name(),
                sample.createdAt().toString(),
                new OrderSummary(order.id(), order.patientId(), order.createdAt().toString()));
    }
}
