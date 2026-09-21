package com.labflow.common.model;

/**
 * A registered patient, as the lab answers it ({@code POST /api/patients} and
 * {@code GET /api/patients/{id}}).
 *
 * @param id   the identifier the lab assigned
 * @param name the name the patient was registered under
 */
public record Patient(String id, String name) {
}
