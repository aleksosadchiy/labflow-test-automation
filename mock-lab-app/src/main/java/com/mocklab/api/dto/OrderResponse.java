package com.mocklab.api.dto;

import com.mocklab.domain.Order;
import com.mocklab.service.OrderDetails;

import java.util.List;

/**
 * An order as returned by the API. Enum values are sent as their names
 * ({@code BLOOD}, {@code ACCESSIONED}) and {@code createdAt} as an ISO-8601 instant.
 */
public record OrderResponse(String id, String patientId, String createdAt, List<SampleSummary> samples) {

    /** The few sample fields an order exposes. */
    public record SampleSummary(String id, String sampleType, String status) {
    }

    /** A freshly placed order: it has no samples yet. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.patientId(), order.createdAt().toString(), List.of());
    }

    public static OrderResponse from(OrderDetails details) {
        Order order = details.order();
        List<SampleSummary> samples = details.samples().stream()
                .map(s -> new SampleSummary(s.id(), s.sampleType().name(), s.status().name()))
                .toList();
        return new OrderResponse(order.id(), order.patientId(), order.createdAt().toString(), samples);
    }
}
