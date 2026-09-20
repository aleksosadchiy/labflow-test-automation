package com.labflow.common.model;

import java.time.Instant;

/**
 * A registered sample, as the lab answers it ({@code POST /api/samples},
 * {@code GET /api/samples/{id}} and each entry of {@code GET /api/samples}). The lab's
 * registration answer also carries a nested {@code order} object; it is not modelled here.
 *
 * @param id         the identifier the lab assigned
 * @param patientId  the patient the sample belongs to
 * @param orderId    the order the sample sits under
 * @param sampleType the kind of sample
 * @param status     the workflow stage the sample is at (a new sample is at ACCESSIONED)
 * @param createdAt  when the lab registered the sample
 */
public record Sample(String id, String patientId, String orderId, SampleType sampleType,
                     WorkflowStage status, Instant createdAt) {
}
