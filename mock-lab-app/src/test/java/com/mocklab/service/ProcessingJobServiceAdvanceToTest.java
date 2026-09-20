package com.mocklab.service;

// @layer: unit
// @spec: 002-mock-lab-rest-api

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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProcessingJobService#advanceTo(String, SampleStatus)}, per
 * functional-spec.md §2.5 and technical-considerations.md §2.5 (only the stage
 * immediately after the current one is accepted; everything else is a 409 with
 * display names; the result/report side effects fire exactly once).
 *
 * <p>No Spring context and no mocks: real in-memory repositories, plus a
 * {@link ReportService} subclass that counts how often each side effect fires
 * (Mockito cannot mock classes on the JDK this project is built with).
 */
class ProcessingJobServiceAdvanceToTest {

    private static final SampleStatus[] STAGES = SampleStatus.values();
    private static final int THREADS = 10;
    private static final int TIMEOUT_SECONDS = 20;

    private final String sampleId = "SMP-" + UUID.randomUUID();
    private final String jobId = "JOB-" + UUID.randomUUID();

    private ProcessingJobRepository processingJobRepository;
    private SampleRepository sampleRepository;
    private TestResultRepository testResultRepository;
    private ReportRepository reportRepository;
    private CountingReportService reportService;
    private ProcessingJobService service;

    @BeforeEach
    void setUp() {
        build(new ProcessingJobRepository());
    }

    private void build(ProcessingJobRepository jobs) {
        processingJobRepository = jobs;
        sampleRepository = new SampleRepository();
        testResultRepository = new TestResultRepository();
        reportRepository = new ReportRepository();
        reportService = new CountingReportService(testResultRepository, reportRepository);
        service = new ProcessingJobService(processingJobRepository, sampleRepository, reportService);
    }

    // ---------------------------------------------------------------- the 8 x 8 table

    static Stream<Arguments> everyCurrentAndRequestedPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (SampleStatus current : STAGES) {
            for (SampleStatus requested : STAGES) {
                pairs.add(Arguments.of(current, requested));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyCurrentAndRequestedPair")
    void onlyTheStageImmediatelyAfterTheCurrentOneIsAccepted(SampleStatus current, SampleStatus requested) {
        seedAt(current);

        if (requested.ordinal() == current.ordinal() + 1) {
            ProcessingJob updated = service.advanceTo(sampleId, requested);

            assertThat(updated.currentStatus()).isEqualTo(requested);
            assertThat(updated.history()).hasSize(current.ordinal() + 2);
            assertThat(updated.history().get(updated.history().size() - 1).status()).isEqualTo(requested);
            assertThat(jobNow()).isEqualTo(updated);
            assertThat(sampleNow().status()).isEqualTo(requested);
            // side effects fire only for the stage that owns them, and only once
            assertThat(reportService.testResultsCreated.get())
                    .isEqualTo(requested == SampleStatus.RESULT_READY ? 1 : 0);
            assertThat(reportService.reportsCreated.get())
                    .isEqualTo(requested == SampleStatus.REPORT_GENERATED ? 1 : 0);
        } else {
            assertThatThrownBy(() -> service.advanceTo(sampleId, requested))
                    .isInstanceOfSatisfying(LabRuleException.class, e -> {
                        assertThat(e.kind()).isEqualTo(FailureKind.CONFLICT);
                        assertThat(e.getMessage())
                                .isEqualTo(LabMessages.invalidTransition(current.displayName(), requested.displayName()));
                    });

            assertNothingChanged(current);
        }
    }

    @Test
    void rejectionMessageUsesDisplayNamesForASkipAhead() {
        seedAt(SampleStatus.PROCESSING);

        assertThatThrownBy(() -> service.advanceTo(sampleId, SampleStatus.RESULT_READY))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.CONFLICT);
                    assertThat(e.getMessage())
                            .isEqualTo("Invalid status transition: cannot move from Processing to Result Ready.");
                });
    }

    @Test
    void rejectionMessageUsesDisplayNamesForAMoveFromTheFirstStage() {
        seedAt(SampleStatus.ACCESSIONED);

        assertThatThrownBy(() -> service.advanceTo(sampleId, SampleStatus.PROCESSING))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.CONFLICT);
                    assertThat(e.getMessage())
                            .isEqualTo("Invalid status transition: cannot move from Accessioned to Processing.");
                });
    }

    // ------------------------------------------------------- side effects, once each

    @Test
    void walkingThroughAllSevenStepsCreatesTheResultAndReportExactlyOnceEach() {
        seedAt(SampleStatus.ACCESSIONED);

        for (int i = 1; i < STAGES.length; i++) {
            SampleStatus target = STAGES[i];

            ProcessingJob updated = service.advanceTo(sampleId, target);

            assertThat(updated.currentStatus()).isEqualTo(target);
            assertThat(updated.history()).hasSize(i + 1);
            assertThat(sampleNow().status()).isEqualTo(target);

            boolean resultExpected = target.ordinal() >= SampleStatus.RESULT_READY.ordinal();
            boolean reportExpected = target == SampleStatus.REPORT_GENERATED;
            assertThat(testResultRepository.findBySampleId(sampleId).isPresent())
                    .as("test result present at %s", target).isEqualTo(resultExpected);
            assertThat(reportRepository.findBySampleId(sampleId).isPresent())
                    .as("report present at %s", target).isEqualTo(reportExpected);
            assertThat(reportService.testResultsCreated.get()).isEqualTo(resultExpected ? 1 : 0);
            assertThat(reportService.reportsCreated.get()).isEqualTo(reportExpected ? 1 : 0);
        }

        assertThat(jobNow().history()).extracting(ProcessingJob.StatusChange::status)
                .containsExactly(STAGES);
    }

    // --------------------------------------------------------------- unknown samples

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "SMP-NOPE"})
    void aNullBlankOrUnknownSampleIdIsNotFound(String unknownId) {
        seedAt(SampleStatus.ACCESSIONED);

        assertThatThrownBy(() -> service.advanceTo(unknownId, SampleStatus.READY_FOR_PROCESSING))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Sample not found.");
                });

        assertNothingChanged(SampleStatus.ACCESSIONED);
    }

    @Test
    void aNullTargetIsAProgrammingErrorNotABusinessError() {
        seedAt(SampleStatus.ACCESSIONED);

        assertThatThrownBy(() -> service.advanceTo(sampleId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("target must not be null");

        assertNothingChanged(SampleStatus.ACCESSIONED);
    }

    // ----------------------------------------------------- mixing advance / advanceTo

    @Test
    void advanceThenAdvanceToTheFollowingStageSucceeds() {
        seedAt(SampleStatus.ACCESSIONED);

        service.advance(sampleId); // -> Ready for Processing
        ProcessingJob updated = service.advanceTo(sampleId, SampleStatus.PROCESSING);

        assertThat(updated.currentStatus()).isEqualTo(SampleStatus.PROCESSING);
        assertThat(updated.history()).extracting(ProcessingJob.StatusChange::status)
                .containsExactly(SampleStatus.ACCESSIONED, SampleStatus.READY_FOR_PROCESSING, SampleStatus.PROCESSING);
        assertThat(sampleNow().status()).isEqualTo(SampleStatus.PROCESSING);
    }

    @Test
    void advanceToThenAdvanceContinuesFromWhereAdvanceToLeftOff() {
        seedAt(SampleStatus.ACCESSIONED);

        service.advanceTo(sampleId, SampleStatus.READY_FOR_PROCESSING);
        Optional<ProcessingJob> updated = service.advance(sampleId);

        assertThat(updated).isPresent();
        assertThat(updated.get().currentStatus()).isEqualTo(SampleStatus.PROCESSING);
        assertThat(jobNow().history()).hasSize(3);
        assertThat(sampleNow().status()).isEqualTo(SampleStatus.PROCESSING);
    }

    @Test
    void advanceToTheStageAdvanceAlreadyReachedIsRejectedNotRepeated() {
        seedAt(SampleStatus.ACCESSIONED);
        service.advance(sampleId); // -> Ready for Processing

        assertThatThrownBy(() -> service.advanceTo(sampleId, SampleStatus.READY_FOR_PROCESSING))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.CONFLICT);
                    assertThat(e.getMessage())
                            .isEqualTo("Invalid status transition: cannot move from Ready for Processing to Ready for Processing.");
                });

        assertNothingChanged(SampleStatus.READY_FOR_PROCESSING);
    }

    @Test
    void advanceStillIsANoOpAtTheFinalStageAndCreatesNothingExtra() {
        seedAt(SampleStatus.REPORT_GENERATED);

        Optional<ProcessingJob> result = service.advance(sampleId);

        assertThat(result).isPresent();
        assertThat(result.get().currentStatus()).isEqualTo(SampleStatus.REPORT_GENERATED);
        assertNothingChanged(SampleStatus.REPORT_GENERATED);
    }

    // ------------------------------------------------------------------ concurrency

    /**
     * Ten threads request the same valid next stage at once; for every stage of
     * the workflow exactly one may win and the rest must be told CONFLICT. The
     * job lookup is deliberately slowed down (a subclass of the in-memory
     * repository) so that, without the {@code synchronized} on the service,
     * several threads would read the same "current" stage and all succeed.
     */
    @RepeatedTest(5)
    void manyThreadsRequestingTheSameNextStageProduceExactlyOneSuccess() throws Exception {
        build(new SlowLookupProcessingJobRepository());
        seedAt(SampleStatus.ACCESSIONED);

        for (int i = 1; i < STAGES.length; i++) {
            SampleStatus target = STAGES[i];

            List<Object> outcomes = raceFor(target);

            long successes = outcomes.stream().filter(o -> o instanceof ProcessingJob).count();
            List<LabRuleException> conflicts = outcomes.stream()
                    .filter(o -> o instanceof LabRuleException)
                    .map(o -> (LabRuleException) o)
                    .toList();
            assertThat(successes).as("successes when moving to %s", target).isEqualTo(1);
            assertThat(conflicts).hasSize(THREADS - 1);
            assertThat(conflicts).allSatisfy(e -> {
                assertThat(e.kind()).isEqualTo(FailureKind.CONFLICT);
                assertThat(e.getMessage())
                        .isEqualTo(LabMessages.invalidTransition(target.displayName(), target.displayName()));
            });

            assertThat(jobNow().currentStatus()).isEqualTo(target);
            assertThat(jobNow().history()).as("history at %s", target).hasSize(i + 1);
            assertThat(sampleNow().status()).isEqualTo(target);
        }

        assertThat(reportService.testResultsCreated.get()).isEqualTo(1);
        assertThat(reportService.reportsCreated.get()).isEqualTo(1);
    }

    /** Starts {@link #THREADS} callers together, each requesting {@code target}. */
    private List<Object> raceFor(SampleStatus target) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                Callable<Object> request = () -> {
                    ready.countDown();
                    if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start latch never released");
                    }
                    try {
                        return service.advanceTo(sampleId, target);
                    } catch (LabRuleException e) {
                        return e;
                    }
                };
                futures.add(pool.submit(request));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("all threads started").isTrue();
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------- helpers

    /** Puts the sample and its job at {@code stage}, one history entry per stage reached. */
    private void seedAt(SampleStatus stage) {
        Instant now = Instant.now();
        sampleRepository.save(new Sample(sampleId, "PAT-1001", "ORD-1001", SampleType.BLOOD, stage, now));
        List<ProcessingJob.StatusChange> history = new ArrayList<>();
        for (int i = 0; i <= stage.ordinal(); i++) {
            history.add(new ProcessingJob.StatusChange(STAGES[i], now));
        }
        processingJobRepository.save(new ProcessingJob(jobId, sampleId, stage, List.copyOf(history)));
    }

    private ProcessingJob jobNow() {
        return processingJobRepository.findBySampleId(sampleId).orElseThrow();
    }

    private Sample sampleNow() {
        return sampleRepository.findById(sampleId).orElseThrow();
    }

    /** After a rejection: job stage, history length, sample mirror and side effects are all as seeded. */
    private void assertNothingChanged(SampleStatus seededStage) {
        ProcessingJob job = jobNow();
        assertThat(job.currentStatus()).isEqualTo(seededStage);
        assertThat(job.history()).hasSize(seededStage.ordinal() + 1);
        assertThat(sampleNow().status()).isEqualTo(seededStage);
        assertThat(testResultRepository.findBySampleId(sampleId)).isEmpty();
        assertThat(reportRepository.findBySampleId(sampleId)).isEmpty();
        assertThat(reportService.testResultsCreated.get()).isZero();
        assertThat(reportService.reportsCreated.get()).isZero();
    }

    /** Real {@link ReportService} that also counts how often each side effect fires. */
    private static final class CountingReportService extends ReportService {
        final AtomicInteger testResultsCreated = new AtomicInteger();
        final AtomicInteger reportsCreated = new AtomicInteger();

        CountingReportService(TestResultRepository testResults, ReportRepository reports) {
            super(testResults, reports);
        }

        @Override
        public TestResult createTestResult(String sampleId) {
            testResultsCreated.incrementAndGet();
            return super.createTestResult(sampleId);
        }

        @Override
        public Report createReport(String sampleId) {
            reportsCreated.incrementAndGet();
            return super.createReport(sampleId);
        }
    }

    /** Pauses after each job lookup so unsynchronised callers would all see the same stage. */
    private static final class SlowLookupProcessingJobRepository extends ProcessingJobRepository {
        @Override
        public Optional<ProcessingJob> findBySampleId(String sampleId) {
            Optional<ProcessingJob> found = super.findBySampleId(sampleId);
            LockSupport.parkNanos(2_000_000L);
            return found;
        }
    }
}
