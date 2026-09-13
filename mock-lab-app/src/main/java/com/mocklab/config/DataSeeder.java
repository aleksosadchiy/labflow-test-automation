package com.mocklab.config;

import com.mocklab.domain.Patient;
import com.mocklab.domain.User;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds fixed reference data on every boot so the app always starts from the
 * same clean, deterministic state. Seeds the one fixed login user, and a
 * small fixed set of Patients with stable, predictable IDs so both UI and
 * API testers can reference known-good and known-bad IDs.
 *
 * <p>The seeded login credentials are externalized to
 * {@code application.yml} (overridable via the {@code MOCK_LAB_USERNAME}
 * and {@code MOCK_LAB_PASSWORD} environment variables) rather than hardcoded
 * here, per the "do not hardcode environment-specific values" principle
 * already applied to {@code BASE_URL}/{@code API_TOKEN} elsewhere in the
 * project. The fixed, known default is still intentional — automation needs
 * a stable, predictable credential to log in with — only its storage
 * location changed.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final String seedUsername;
    private final String seedPassword;

    public DataSeeder(UserRepository userRepository,
                       PatientRepository patientRepository,
                       @Value("${mock-lab.seed-user.username}") String seedUsername,
                       @Value("${mock-lab.seed-user.password}") String seedPassword) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        userRepository.save(new User(seedUsername, seedPassword));

        patientRepository.save(new Patient("PAT-1001", "Alice Johnson"));
        patientRepository.save(new Patient("PAT-1002", "Brian Smith"));
        patientRepository.save(new Patient("PAT-1003", "Carla Diaz"));
        patientRepository.save(new Patient("PAT-1004", "David Chen"));
        patientRepository.save(new Patient("PAT-1005", "Emily Turner"));
        patientRepository.save(new Patient("PAT-1006", "Farid Haidari"));
        patientRepository.save(new Patient("PAT-1007", "Grace Kim"));
    }
}
