// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.repository.TestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SampleQueryService}: looking up and listing samples.
 * Plain objects, no Spring context.
 */
class SampleQueryServiceTest {

    private static final String PATIENT_ID = "PAT-1001";
    private static final String ORDER_ID = "ORD-1";
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;
    private SampleQueryService service;

    @BeforeEach
    void setUp() {
        sampleRepository = new SampleRepository();
        processingJobRepository = new ProcessingJobRepository();
        service = new SampleQueryService(
                sampleRepository, processingJobRepository, new TestResultRepository(), new ReportRepository());
    }

    private Sample saveSample(String id, SampleType type, SampleStatus storedStatus, Instant createdAt) {
        return sampleRepository.save(new Sample(id, PATIENT_ID, ORDER_ID, type, storedStatus, createdAt));
    }

    private void saveJob(String sampleId, SampleStatus currentStatus) {
        processingJobRepository.save(new ProcessingJob(
                "JOB-" + sampleId, sampleId, currentStatus,
                List.of(new ProcessingJob.StatusChange(currentStatus, T0))));
    }

    // ---- get ----

    @Test
    void getReturnsEveryFieldOfTheSample() {
        saveSample("SMP-1", SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveJob("SMP-1", SampleStatus.ACCESSIONED);

        SampleView view = service.get("SMP-1");

        assertThat(view).isEqualTo(new SampleView(
                "SMP-1", PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.ACCESSIONED, T0));
    }

    @Test
    void getTakesTheStatusFromTheProcessingJobNotTheStoredMirror() {
        // the sample mirror lags behind the job (the job is saved first, the mirror after)
        saveSample("SMP-1", SampleType.BLOOD, SampleStatus.PROCESSING, T0);
        saveJob("SMP-1", SampleStatus.SEQUENCING);

        assertThat(service.get("SMP-1").status()).isEqualTo(SampleStatus.SEQUENCING);
    }

    @Test
    void getFallsBackToTheStoredStatusWhenTheSampleHasNoJob() {
        saveSample("SMP-1", SampleType.SWAB, SampleStatus.READY_FOR_PROCESSING, T0);

        assertThat(service.get("SMP-1").status()).isEqualTo(SampleStatus.READY_FOR_PROCESSING);
    }

    @Test
    void getUsesTheJobBelongingToThatSampleOnly() {
        saveSample("SMP-1", SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveSample("SMP-2", SampleType.URINE, SampleStatus.ACCESSIONED, T0);
        saveJob("SMP-1", SampleStatus.QUALITY_CHECK);
        saveJob("SMP-2", SampleStatus.RESULT_READY);

        assertThat(service.get("SMP-1").status()).isEqualTo(SampleStatus.QUALITY_CHECK);
        assertThat(service.get("SMP-2").status()).isEqualTo(SampleStatus.RESULT_READY);
    }

    @Test
    void getRejectsAnUnknownIdAsNotFound() {
        saveSample("SMP-1", SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);

        assertThatThrownBy(() -> service.get("SMP-does-not-exist"))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.SAMPLE_NOT_FOUND);
                });
        assertThat(LabMessages.SAMPLE_NOT_FOUND).isEqualTo("Sample not found.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", " \n "})
    void getRejectsNullBlankAndWhitespaceOnlyIdsAsNotFound(String id) {
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.SAMPLE_NOT_FOUND);
                });
    }

    // ---- list ----

    @Test
    void listIsEmptyWhenNoSamplesAreRegistered() {
        assertThat(service.list()).isEmpty();
    }

    @Test
    void listReturnsEverySampleInCreatedAtThenIdOrder() {
        // saved out of order; SMP-a and SMP-b share a timestamp so the id tie-break matters
        saveSample("SMP-late", SampleType.URINE, SampleStatus.ACCESSIONED, T0.plusSeconds(60));
        saveSample("SMP-b", SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveSample("SMP-a", SampleType.SWAB, SampleStatus.ACCESSIONED, T0);
        saveSample("SMP-early", SampleType.TISSUE, SampleStatus.ACCESSIONED, T0.minusSeconds(60));

        assertThat(service.list()).extracting(SampleView::id)
                .containsExactly("SMP-early", "SMP-a", "SMP-b", "SMP-late");
    }

    @Test
    void listTakesEachSamplesStatusFromItsJobFallingBackToTheStoredStatus() {
        saveSample("SMP-a", SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveJob("SMP-a", SampleStatus.BIOINFORMATICS);
        saveSample("SMP-b", SampleType.SWAB, SampleStatus.PROCESSING, T0.plusSeconds(1));
        saveJob("SMP-b", SampleStatus.RESULT_READY);
        saveSample("SMP-c", SampleType.URINE, SampleStatus.READY_FOR_PROCESSING, T0.plusSeconds(2));

        assertThat(service.list()).containsExactly(
                new SampleView("SMP-a", PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.BIOINFORMATICS, T0),
                new SampleView("SMP-b", PATIENT_ID, ORDER_ID, SampleType.SWAB, SampleStatus.RESULT_READY,
                        T0.plusSeconds(1)),
                new SampleView("SMP-c", PATIENT_ID, ORDER_ID, SampleType.URINE, SampleStatus.READY_FOR_PROCESSING,
                        T0.plusSeconds(2)));
    }
}
