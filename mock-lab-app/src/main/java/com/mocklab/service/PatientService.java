package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Patient;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Registers and looks up {@link Patient} records for the REST API, per
 * functional-spec.md §2.2 and technical-considerations.md §2.3.
 *
 * <p>Names are trimmed, may repeat, and have no length limit. Generated IDs are
 * {@code PAT-<UUID>}, so they can never collide with the seeded
 * {@code PAT-1001..PAT-1007}. Failures are reported as {@link LabRuleException}
 * so the API layer can map them to the standard error body.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /**
     * Saves a new patient with a generated ID.
     *
     * @throws LabRuleException {@code VALIDATION} when {@code name} is null or blank
     */
    public Patient register(String name) {
        if (name == null || name.isBlank()) {
            throw new LabRuleException(FailureKind.VALIDATION, LabMessages.PATIENT_NAME_REQUIRED);
        }
        return patientRepository.save(new Patient(generateId("PAT"), name.strip()));
    }

    /**
     * Returns the patient with the given ID.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown
     */
    public Patient find(String id) {
        if (id == null || id.isBlank()) {
            throw new LabRuleException(FailureKind.NOT_FOUND, LabMessages.PATIENT_NOT_FOUND);
        }
        return patientRepository.findById(id)
                .orElseThrow(() -> new LabRuleException(FailureKind.NOT_FOUND, LabMessages.PATIENT_NOT_FOUND));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
