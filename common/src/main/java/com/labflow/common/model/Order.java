package com.labflow.common.model;

import java.time.Instant;
import java.util.List;

/**
 * A placed order, as the lab answers it ({@code POST /api/orders} and
 * {@code GET /api/orders/{id}}).
 *
 * @param id        the identifier the lab assigned
 * @param patientId the patient the order was placed for
 * @param createdAt when the lab created the order
 * @param samples   the samples registered under the order; never {@code null} (an answer
 *                  without the list reads as an empty one) and never modifiable
 */
public record Order(String id, String patientId, Instant createdAt, List<SampleSummary> samples) {

    public Order {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
