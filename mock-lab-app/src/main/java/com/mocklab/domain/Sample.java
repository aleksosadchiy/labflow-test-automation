package com.mocklab.domain;

import java.time.Instant;

/**
 * A registered lab sample. {@code status} is a denormalized mirror of the
 * associated {@link ProcessingJob#currentStatus()}, kept in sync on every
 * advance (that sync logic is a later slice — this record just holds the
 * shape).
 */
public record Sample(
        String id,
        String patientId,
        String orderId,
        SampleType sampleType,
        SampleStatus status,
        Instant createdAt) {
}
