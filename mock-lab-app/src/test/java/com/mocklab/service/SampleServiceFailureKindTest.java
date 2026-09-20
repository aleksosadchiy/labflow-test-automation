// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.domain.Patient;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.exception.FailureKind;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link FailureKind} that {@link SampleCreationResult.Failure}
 * carries for each {@link SampleService#createSample(String, String)} rule, and for
 * the rule precedence (first failure wins). Every failure is asserted on both its
 * exact message and its kind; the message text is unchanged from spec 001 because
 * the screens still display it verbatim.
 */
class SampleServiceFailureKindTest {

    private static final String KNOWN_PATIENT_ID = "PAT-1001";

    private PatientRepository patientRepository;
    private OrderRepository orderRepository;
    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;
    private SampleService sampleService;

    @BeforeEach
    void setUp() {
        patientRepository = new PatientRepository();
        patientRepository.save(new Patient(KNOWN_PATIENT_ID, "Alice Johnson"));
        orderRepository = new OrderRepository();
        sampleRepository = new SampleRepository();
        processingJobRepository = new ProcessingJobRepository();
        sampleService = new SampleService(
                patientRepository, orderRepository, sampleRepository, processingJobRepository);
    }

    @Test
    void blankPatientIsAValidationFailure() {
        assertFailure(sampleService.createSample("  ", "BLOOD"),
                FailureKind.VALIDATION, "Patient is required.");
        assertFailure(sampleService.createSample(null, "BLOOD"),
                FailureKind.VALIDATION, "Patient is required.");
    }

    @Test
    void blankSampleTypeIsAValidationFailure() {
        assertFailure(sampleService.createSample(KNOWN_PATIENT_ID, ""),
                FailureKind.VALIDATION, "Sample type is required.");
        assertFailure(sampleService.createSample(KNOWN_PATIENT_ID, null),
                FailureKind.VALIDATION, "Sample type is required.");
    }

    @Test
    void unknownPatientIsANotFoundFailure() {
        assertFailure(sampleService.createSample("PAT-9999", "BLOOD"),
                FailureKind.NOT_FOUND, "Patient not found.");
    }

    @Test
    void invalidSampleTypeIsAValidationFailure() {
        assertFailure(sampleService.createSample(KNOWN_PATIENT_ID, "PLASMA"),
                FailureKind.VALIDATION, "Invalid sample type.");
    }

    @Test
    void generatedIdCollisionIsAConflictFailure() {
        SampleService colliding = serviceWhereEveryGeneratedIdCollides();

        SampleCreationResult result = colliding.createSample(KNOWN_PATIENT_ID, "BLOOD");

        assertFailure(result, FailureKind.CONFLICT, "A sample with this ID already exists.");
    }

    @Test
    void blankPatientBeatsEveryOtherProblem() {
        // blank patient + blank type (and an invalid-looking type elsewhere): patient rule is first
        assertFailure(sampleService.createSample("", ""),
                FailureKind.VALIDATION, "Patient is required.");
        assertFailure(sampleService.createSample("", "PLASMA"),
                FailureKind.VALIDATION, "Patient is required.");
    }

    @Test
    void blankTypeBeatsUnknownPatient() {
        assertFailure(sampleService.createSample("PAT-9999", ""),
                FailureKind.VALIDATION, "Sample type is required.");
    }

    @Test
    void unknownPatientBeatsInvalidType() {
        assertFailure(sampleService.createSample("PAT-9999", "PLASMA"),
                FailureKind.NOT_FOUND, "Patient not found.");
    }

    @Test
    void invalidTypeBeatsGeneratedIdCollision() {
        SampleService colliding = serviceWhereEveryGeneratedIdCollides();

        assertFailure(colliding.createSample(KNOWN_PATIENT_ID, "PLASMA"),
                FailureKind.VALIDATION, "Invalid sample type.");
    }

    @Test
    void validRequestIsStillASuccess() {
        SampleCreationResult result = sampleService.createSample(KNOWN_PATIENT_ID, "BLOOD");

        assertThat(result).isInstanceOf(SampleCreationResult.Success.class);
        SampleCreationResult.Success success = (SampleCreationResult.Success) result;
        assertThat(success.sample().status()).isEqualTo(SampleStatus.ACCESSIONED);
        assertThat(sampleRepository.findById(success.sample().id())).isPresent();
    }

    /** Forces the defensive check: a sample repository that claims every ID is already taken. */
    private SampleService serviceWhereEveryGeneratedIdCollides() {
        SampleRepository everyIdTaken = new SampleRepository() {
            @Override
            public Optional<Sample> findById(String id) {
                return Optional.of(new Sample(id, KNOWN_PATIENT_ID, "ORD-existing",
                        SampleType.BLOOD, SampleStatus.ACCESSIONED, Instant.EPOCH));
            }
        };
        return new SampleService(
                patientRepository, orderRepository, everyIdTaken, processingJobRepository);
    }

    private static void assertFailure(SampleCreationResult result, FailureKind kind, String message) {
        assertThat(result).isInstanceOf(SampleCreationResult.Failure.class);
        SampleCreationResult.Failure failure = (SampleCreationResult.Failure) result;
        assertThat(failure.message()).isEqualTo(message);
        assertThat(failure.kind()).isEqualTo(kind);
    }
}
