package com.mocklab.config;

import com.mocklab.domain.Patient;
import com.mocklab.domain.User;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds fixed reference data on every boot so the app always starts from the
 * same clean, deterministic state. Seeds the one fixed login user, and a
 * small fixed set of Patients with stable, predictable IDs so both UI and
 * API testers can reference known-good and known-bad IDs.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public DataSeeder(UserRepository userRepository, PatientRepository patientRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    public void run(String... args) {
        userRepository.save(new User("labtech", "password123"));

        patientRepository.save(new Patient("PAT-1001", "Alice Johnson"));
        patientRepository.save(new Patient("PAT-1002", "Brian Smith"));
        patientRepository.save(new Patient("PAT-1003", "Carla Diaz"));
        patientRepository.save(new Patient("PAT-1004", "David Chen"));
        patientRepository.save(new Patient("PAT-1005", "Emily Turner"));
        patientRepository.save(new Patient("PAT-1006", "Farid Haidari"));
        patientRepository.save(new Patient("PAT-1007", "Grace Kim"));
    }
}
