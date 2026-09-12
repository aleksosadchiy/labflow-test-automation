package com.mocklab.service;

import com.mocklab.domain.Patient;
import com.mocklab.domain.SampleStatus;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SampleService#createSample(String, String)}.
 *
 * @spec: 001-mock-lab-application
 */
class SampleServiceTest {

    private static final String KNOWN_PATIENT_ID = "PAT-1001";

    private SampleService sampleService;
    private PatientRepository patientRepository;
    private OrderRepository orderRepository;
    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;

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
    void createsSampleOrderAndProcessingJobOnValidInput() {
        SampleCreationResult result = sampleService.createSample(KNOWN_PATIENT_ID, "BLOOD");

        assertThat(result).isInstanceOf(SampleCreationResult.Success.class);
        SampleCreationResult.Success success = (SampleCreationResult.Success) result;

        assertThat(success.sample().status()).isEqualTo(SampleStatus.ACCESSIONED);
        assertThat(success.sample().patientId()).isEqualTo(KNOWN_PATIENT_ID);
        assertThat(success.sample().orderId()).isEqualTo(success.order().id());
        assertThat(success.order().patientId()).isEqualTo(KNOWN_PATIENT_ID);
        assertThat(success.processingJob().sampleId()).isEqualTo(success.sample().id());
        assertThat(success.processingJob().currentStatus()).isEqualTo(SampleStatus.ACCESSIONED);
        assertThat(success.processingJob().history()).hasSize(1);
        assertThat(success.processingJob().history().get(0).status()).isEqualTo(SampleStatus.ACCESSIONED);

        // and everything was actually persisted, not just returned
        assertThat(sampleRepository.findById(success.sample().id())).isPresent();
        assertThat(orderRepository.findById(success.order().id())).isPresent();
        assertThat(processingJobRepository.findById(success.processingJob().id())).isPresent();
    }

    @Test
    void caseInsensitiveSampleTypeMatches() {
        SampleCreationResult result = sampleService.createSample(KNOWN_PATIENT_ID, "blood");

        assertThat(result).isInstanceOf(SampleCreationResult.Success.class);
        SampleCreationResult.Success success = (SampleCreationResult.Success) result;
        assertThat(success.sample().sampleType().name()).isEqualTo("BLOOD");
    }

    @Test
    void blankPatientIdIsRejected() {
        SampleCreationResult result = sampleService.createSample("", "BLOOD");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Patient is required."));
    }

    @Test
    void nullPatientIdIsRejected() {
        SampleCreationResult result = sampleService.createSample(null, "BLOOD");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Patient is required."));
    }

    @Test
    void blankSampleTypeIsRejected() {
        SampleCreationResult result = sampleService.createSample(KNOWN_PATIENT_ID, "  ");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Sample type is required."));
    }

    @Test
    void unknownPatientIdIsRejected() {
        SampleCreationResult result = sampleService.createSample("PAT-9999", "BLOOD");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Patient not found."));
    }

    @Test
    void invalidSampleTypeIsRejected() {
        SampleCreationResult result = sampleService.createSample(KNOWN_PATIENT_ID, "PLASMA");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Invalid sample type."));
    }

    @Test
    void validationOrderPrefersBlankPatientOverBlankType() {
        // both blank -> patient check must win (it's checked first)
        SampleCreationResult result = sampleService.createSample("", "");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Patient is required."));
    }

    @Test
    void validationOrderPrefersPatientNotFoundOverInvalidType() {
        // unknown patient AND invalid type -> patient-not-found must win (checked first)
        SampleCreationResult result = sampleService.createSample("PAT-9999", "PLASMA");

        assertThat(result).isEqualTo(new SampleCreationResult.Failure("Patient not found."));
    }
}
