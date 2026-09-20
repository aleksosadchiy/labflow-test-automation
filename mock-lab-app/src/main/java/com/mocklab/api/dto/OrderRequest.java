package com.mocklab.api.dto;

/**
 * Body of {@code POST /api/orders}. Only {@code patientId} is read; any other
 * field a client sends (for example an {@code id}) is ignored, because the lab
 * assigns IDs.
 */
public record OrderRequest(String patientId) {
}
