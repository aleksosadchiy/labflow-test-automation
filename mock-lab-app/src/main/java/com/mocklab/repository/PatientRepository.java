package com.mocklab.repository;

import com.mocklab.domain.Patient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link Patient} records, keyed by patient ID.
 * Nothing survives a JVM restart — {@link com.mocklab.config.DataSeeder}
 * repopulates it on every boot.
 */
@Repository
public class PatientRepository {

    private final ConcurrentHashMap<String, Patient> patientsById = new ConcurrentHashMap<>();

    public Optional<Patient> findById(String id) {
        return Optional.ofNullable(patientsById.get(id));
    }

    public Patient save(Patient patient) {
        patientsById.put(patient.id(), patient);
        return patient;
    }
}
