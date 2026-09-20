// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Report;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.domain.TestResult;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.repository.TestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the progress, result and report reads of {@link SampleQueryService}.
 * Samples are moved through the workflow by the real {@link ProcessingJobService} and
 * {@link ReportService} (plain objects, no Spring context), so the stored data is the
 * same a running app would hold.
 */
class SampleQueryServiceProgressTest {

    private static final String SAMPLE_ID = "SMP-1";
    private static final String PATIENT_ID = "PAT-1001";
    private static final String ORDER_ID = "ORD-1";
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;
    private TestResultRepository testResultRepository;
    private ReportRepository reportRepository;
    private ProcessingJobService processingJobService;
    private SampleQueryService service;

    @BeforeEach
    void setUp() {
        sampleRepository = new SampleRepository();
        processingJobRepository = new ProcessingJobRepository();
        testResultRepository = new TestResultRepository();
        reportRepository = new ReportRepository();
        processingJobService = new ProcessingJobService(
                processingJobRepository, sampleRepository, new ReportService(testResultRepository, reportRepository));
        service = new SampleQueryService(
                sampleRepository, processingJobRepository, testResultRepository, reportRepository);
    }

    /** Registers a sample with its job at Accessioned, then advances it {@code stage.ordinal()} times. */
    private void sampleAt(SampleStatus stage) {
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.ACCESSIONED, T0));
        processingJobRepository.save(new ProcessingJob(
                "JOB-" + SAMPLE_ID, SAMPLE_ID, SampleStatus.ACCESSIONED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.ACCESSIONED, T0))));
        for (int i = 0; i < stage.ordinal(); i++) {
            processingJobService.advance(SAMPLE_ID);
        }
    }

    private static void assertLabRule(Throwable thrown, FailureKind kind, String message) {
        assertThat(thrown).isInstanceOfSatisfying(LabRuleException.class, e -> {
            assertThat(e.kind()).isEqualTo(kind);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    // ---- result / report availability, at every stage ----

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void resultIsAvailableExactlyFromResultReadyOnwards(SampleStatus stage) {
        sampleAt(stage);

        if (stage.ordinal() >= SampleStatus.RESULT_READY.ordinal()) {
            TestResult stored = testResultRepository.findBySampleId(SAMPLE_ID).orElseThrow();
            assertThat(service.result(SAMPLE_ID))
                    .isEqualTo(new ResultView(SAMPLE_ID, stored.testName(), stored.resultValue()));
        } else {
            assertThatThrownBy(() -> service.result(SAMPLE_ID))
                    .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, "Test result is not ready yet."));
        }
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void reportIsAvailableExactlyAtReportGenerated(SampleStatus stage) {
        sampleAt(stage);

        if (stage == SampleStatus.REPORT_GENERATED) {
            TestResult storedResult = testResultRepository.findBySampleId(SAMPLE_ID).orElseThrow();
            Report storedReport = reportRepository.findBySampleId(SAMPLE_ID).orElseThrow();
            assertThat(service.report(SAMPLE_ID)).isEqualTo(new ReportView(
                    SAMPLE_ID, storedResult.testName(), storedResult.resultValue(), storedReport.generatedAt()));
        } else {
            assertThatThrownBy(() -> service.report(SAMPLE_ID))
                    .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, "Report is not ready yet."));
        }
    }

    @Test
    void aSampleAtReportGeneratedStillReturnsItsResult() {
        sampleAt(SampleStatus.REPORT_GENERATED);

        ResultView result = service.result(SAMPLE_ID);

        assertThat(result.sampleId()).isEqualTo(SAMPLE_ID);
        assertThat(result.testName()).isNotBlank();
        assertThat(result.resultValue()).isNotBlank();
    }

    @Test
    void notReadyIsAConflictNeverANotFound() {
        sampleAt(SampleStatus.QUALITY_CHECK);

        assertThatThrownBy(() -> service.result(SAMPLE_ID))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.kind()).isNotEqualTo(FailureKind.NOT_FOUND));
        assertThatThrownBy(() -> service.report(SAMPLE_ID))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.kind()).isNotEqualTo(FailureKind.NOT_FOUND));
        assertThat(LabMessages.RESULT_NOT_READY).isEqualTo("Test result is not ready yet.");
        assertThat(LabMessages.REPORT_NOT_READY).isEqualTo("Report is not ready yet.");
    }

    // ---- progress ----

    @Test
    void progressAtAccessionedHasExactlyOneEntry() {
        sampleAt(SampleStatus.ACCESSIONED);

        ProgressView progress = service.progress(SAMPLE_ID);

        assertThat(progress.sampleId()).isEqualTo(SAMPLE_ID);
        assertThat(progress.currentStatus()).isEqualTo(SampleStatus.ACCESSIONED);
        assertThat(progress.stages()).containsExactly(
                new ProgressView.StageEntry(SampleStatus.ACCESSIONED, T0));
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void progressListsEveryReachedStageInWorkflowOrderIncludingTheCurrentOne(SampleStatus stage) {
        sampleAt(stage);
        int advances = stage.ordinal();

        ProgressView progress = service.progress(SAMPLE_ID);

        List<SampleStatus> expectedOrder = Arrays.asList(SampleStatus.values()).subList(0, advances + 1);
        assertThat(progress.stages()).hasSize(advances + 1);
        assertThat(progress.stages()).extracting(ProgressView.StageEntry::status).containsExactlyElementsOf(expectedOrder);
        assertThat(progress.stages()).extracting(ProgressView.StageEntry::reachedAt).doesNotContainNull();
        assertThat(progress.currentStatus()).isEqualTo(stage);
        assertThat(progress.currentStatus()).isEqualTo(progress.stages().get(advances).status());
    }

    @Test
    void progressTimesNeverGoBackwards() {
        sampleAt(SampleStatus.REPORT_GENERATED);

        List<Instant> times = service.progress(SAMPLE_ID).stages().stream()
                .map(ProgressView.StageEntry::reachedAt)
                .toList();

        assertThat(times).isSorted();
    }

    @Test
    void progressTakesTheCurrentStageFromTheJobNotTheStoredMirror() {
        // the sample mirror lags behind the job
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.PROCESSING, T0));
        processingJobRepository.save(new ProcessingJob("JOB-1", SAMPLE_ID, SampleStatus.SEQUENCING, List.of(
                new ProcessingJob.StatusChange(SampleStatus.ACCESSIONED, T0),
                new ProcessingJob.StatusChange(SampleStatus.READY_FOR_PROCESSING, T0.plusSeconds(1)),
                new ProcessingJob.StatusChange(SampleStatus.PROCESSING, T0.plusSeconds(2)),
                new ProcessingJob.StatusChange(SampleStatus.SEQUENCING, T0.plusSeconds(3)))));

        ProgressView progress = service.progress(SAMPLE_ID);

        assertThat(progress.currentStatus()).isEqualTo(SampleStatus.SEQUENCING);
        assertThat(progress.stages()).extracting(ProgressView.StageEntry::status).last().isEqualTo(SampleStatus.SEQUENCING);
    }

    @Test
    void progressFallsBackToASingleEntryForTheStoredStatusWhenThereIsNoJob() {
        Instant createdAt = T0.plusSeconds(42);
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.SWAB, SampleStatus.READY_FOR_PROCESSING, createdAt));

        ProgressView progress = service.progress(SAMPLE_ID);

        assertThat(progress).isEqualTo(new ProgressView(SAMPLE_ID, SampleStatus.READY_FOR_PROCESSING, List.of(
                new ProgressView.StageEntry(SampleStatus.READY_FOR_PROCESSING, createdAt))));
    }

    // ---- readiness follows the job, falling back to the stored status ----

    @Test
    void readinessIsDecidedByTheJobNotTheStoredMirror() {
        // job is done, mirror still says Accessioned
        sampleAt(SampleStatus.REPORT_GENERATED);
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.ACCESSIONED, T0));

        assertThat(service.result(SAMPLE_ID).sampleId()).isEqualTo(SAMPLE_ID);
        assertThat(service.report(SAMPLE_ID).sampleId()).isEqualTo(SAMPLE_ID);
    }

    @Test
    void readinessIsNotGrantedByAnAheadOfTheJobStoredMirror() {
        // mirror claims the end, the job is still at Processing
        sampleAt(SampleStatus.PROCESSING);
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.REPORT_GENERATED, T0));

        assertThatThrownBy(() -> service.result(SAMPLE_ID))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.RESULT_NOT_READY));
        assertThatThrownBy(() -> service.report(SAMPLE_ID))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.REPORT_NOT_READY));
    }

    @Test
    void withoutAJobTheStoredStatusDecidesReadiness() {
        sampleRepository.save(new Sample(
                SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, SampleStatus.SEQUENCING, T0));

        assertThatThrownBy(() -> service.result(SAMPLE_ID))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.RESULT_NOT_READY));
        assertThatThrownBy(() -> service.report(SAMPLE_ID))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.REPORT_NOT_READY));
    }

    // ---- unknown / blank ids ----

    @Test
    void unknownIdIsNotFoundForProgressResultAndReport() {
        sampleAt(SampleStatus.REPORT_GENERATED);

        assertThatThrownBy(() -> service.progress("SMP-does-not-exist"))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
        assertThatThrownBy(() -> service.result("SMP-does-not-exist"))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
        assertThatThrownBy(() -> service.report("SMP-does-not-exist"))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", " \n "})
    void nullBlankAndWhitespaceIdsAreNotFoundForProgressResultAndReport(String id) {
        assertThatThrownBy(() -> service.progress(id))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
        assertThatThrownBy(() -> service.result(id))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
        assertThatThrownBy(() -> service.report(id))
                .satisfies(e -> assertLabRule(e, FailureKind.NOT_FOUND, "Sample not found."));
    }

    // ---- data-integrity failures ----

    @Test
    void resultThrowsIllegalStateWhenTheStageIsReadyButNoResultIsStored() {
        sampleAt(SampleStatus.QUALITY_CHECK);
        // a job at Result Ready that never went through ReportService: no stored result
        processingJobRepository.save(new ProcessingJob("JOB-" + SAMPLE_ID, SAMPLE_ID, SampleStatus.RESULT_READY,
                List.of(new ProcessingJob.StatusChange(SampleStatus.RESULT_READY, T0))));

        assertThatThrownBy(() -> service.result(SAMPLE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No TestResult found");
    }

    @Test
    void reportThrowsIllegalStateWhenTheStageIsReportGeneratedButNoResultIsStored() {
        sampleAt(SampleStatus.QUALITY_CHECK);
        processingJobRepository.save(new ProcessingJob("JOB-" + SAMPLE_ID, SAMPLE_ID, SampleStatus.REPORT_GENERATED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.REPORT_GENERATED, T0))));

        assertThatThrownBy(() -> service.report(SAMPLE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No TestResult found");
    }

    @Test
    void reportThrowsIllegalStateWhenTheStageIsReportGeneratedButNoReportIsStored() {
        sampleAt(SampleStatus.RESULT_READY);
        processingJobRepository.save(new ProcessingJob("JOB-" + SAMPLE_ID, SAMPLE_ID, SampleStatus.REPORT_GENERATED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.REPORT_GENERATED, T0))));

        assertThatThrownBy(() -> service.report(SAMPLE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Report found");
    }

    // ---- isolation between samples ----

    @Test
    void resultAndReportBelongToTheRequestedSampleOnly() {
        sampleAt(SampleStatus.REPORT_GENERATED);
        // a second sample that has not got that far
        sampleRepository.save(new Sample(
                "SMP-2", PATIENT_ID, ORDER_ID, SampleType.URINE, SampleStatus.ACCESSIONED, T0));
        processingJobRepository.save(new ProcessingJob("JOB-SMP-2", "SMP-2", SampleStatus.ACCESSIONED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.ACCESSIONED, T0))));

        assertThatThrownBy(() -> service.result("SMP-2"))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.RESULT_NOT_READY));
        assertThatThrownBy(() -> service.report("SMP-2"))
                .satisfies(e -> assertLabRule(e, FailureKind.CONFLICT, LabMessages.REPORT_NOT_READY));
        assertThat(service.report(SAMPLE_ID).sampleId()).isEqualTo(SAMPLE_ID);
    }
}
