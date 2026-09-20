package com.mocklab.api.dto;

import com.mocklab.domain.Patient;

/**
 * A patient as returned by the API: the lab-assigned ID and the (trimmed) name.
 */
public record PatientResponse(String id, String name) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.id(), patient.name());
    }
}
