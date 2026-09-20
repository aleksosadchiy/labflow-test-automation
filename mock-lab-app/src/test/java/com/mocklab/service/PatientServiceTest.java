// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Patient;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PatientService}: registering and looking up patients.
 * Plain objects, no Spring context.
 */
class PatientServiceTest {

    private static final Set<String> SEEDED_IDS = Set.of(
            "PAT-1001", "PAT-1002", "PAT-1003", "PAT-1004", "PAT-1005", "PAT-1006", "PAT-1007");

    private PatientRepository patientRepository;
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientRepository = new PatientRepository();
        patientService = new PatientService(patientRepository);
    }

    // ---- register ----

    @Test
    void registerSavesAndReturnsThePatient() {
        Patient patient = patientService.register("Alice Johnson");

        assertThat(patient.name()).isEqualTo("Alice Johnson");
        assertThat(patientRepository.findById(patient.id())).contains(patient);
    }

    @Test
    void registerTrimsTheName() {
        Patient patient = patientService.register("  Alice Johnson \t");

        assertThat(patient.name()).isEqualTo("Alice Johnson");
        assertThat(patientRepository.findById(patient.id()).orElseThrow().name()).isEqualTo("Alice Johnson");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", " \n "})
    void registerRejectsNullBlankAndWhitespaceOnlyNames(String name) {
        assertThatThrownBy(() -> patientService.register(name))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.VALIDATION);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_NAME_REQUIRED);
                });
        assertThat(LabMessages.PATIENT_NAME_REQUIRED).isEqualTo("Patient name is required.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void failedRegisterSavesNothing(String name) {
        AtomicInteger saves = new AtomicInteger();
        PatientService service = new PatientService(new PatientRepository() {
            @Override
            public Patient save(Patient patient) {
                saves.incrementAndGet();
                return super.save(patient);
            }
        });

        assertThatThrownBy(() -> service.register(name)).isInstanceOf(LabRuleException.class);

        assertThat(saves).hasValue(0);
    }

    @Test
    void generatedIdsStartWithPatPrefix() {
        Patient patient = patientService.register("Alice Johnson");

        assertThat(patient.id()).startsWith("PAT-");
        assertThat(patient.id()).hasSizeGreaterThan("PAT-".length());
    }

    @Test
    void generatedIdsAreUniqueAndNeverCollideWithSeededIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            Patient patient = patientService.register("Patient " + i);
            assertThat(patient.id()).startsWith("PAT-");
            ids.add(patient.id());
        }

        assertThat(ids).hasSize(1_000);
        assertThat(ids).doesNotContainAnyElementsOf(SEEDED_IDS);
    }

    @Test
    void repeatedNamesAreAllowedAndGetDistinctIds() {
        Patient first = patientService.register("Alice Johnson");
        Patient second = patientService.register("Alice Johnson");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.name()).isEqualTo(first.name());
        assertThat(patientService.find(first.id())).isEqualTo(first);
        assertThat(patientService.find(second.id())).isEqualTo(second);
    }

    @Test
    void registerDoesNotLimitTheNameLength() {
        String longName = "A".repeat(10_000);

        Patient patient = patientService.register(longName);

        assertThat(patient.name()).isEqualTo(longName);
    }

    // ---- find ----

    @Test
    void findReturnsARegisteredPatient() {
        Patient registered = patientService.register("Alice Johnson");

        assertThat(patientService.find(registered.id())).isEqualTo(registered);
    }

    @Test
    void findReturnsAManuallySeededPatient() {
        Patient seeded = patientRepository.save(new Patient("PAT-1001", "Alice Johnson"));

        assertThat(patientService.find("PAT-1001")).isEqualTo(seeded);
    }

    @Test
    void findRejectsAnUnknownId() {
        assertThatThrownBy(() -> patientService.find("PAT-does-not-exist"))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_NOT_FOUND);
                });
        assertThat(LabMessages.PATIENT_NOT_FOUND).isEqualTo("Patient not found.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   "})
    void findRejectsNullAndBlankIdsAsNotFound(String id) {
        assertThatThrownBy(() -> patientService.find(id))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_NOT_FOUND);
                });
    }

    @Test
    void findDoesNotCreateAPatientForAnUnknownId() {
        assertThatThrownBy(() -> patientService.find("PAT-ghost"))
                .isInstanceOf(LabRuleException.class);

        assertThat(patientRepository.findById("PAT-ghost")).isEmpty();
    }
}
