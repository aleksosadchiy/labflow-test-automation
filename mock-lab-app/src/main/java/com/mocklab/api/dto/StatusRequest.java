package com.mocklab.api.dto;

/**
 * Body of {@code PUT /api/samples/{id}/status}: the stage the caller wants the
 * sample to move to. Either the enum name ({@code READY_FOR_PROCESSING}) or the
 * display name ({@code Ready for Processing}) is accepted, in any letter case.
 * Any other field a client sends is ignored.
 */
public record StatusRequest(String status) {
}
