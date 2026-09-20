package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Report;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.TestResult;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.repository.TestResultRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read side of samples for the REST API, per functional-spec.md §2.4/§2.6 and
 * technical-considerations.md §2.2-2.3: looking samples up and listing them, and
 * reading a sample's progress, test result and report.
 *
 * <p>Each sample's status is read from its processing job (the source of truth),
 * falling back to the sample's own stored status only when it has no job. That
 * status also decides whether the result and report are available yet: the result
 * from Result Ready onwards, the report only at Report Generated (the same rule the
 * View Report screen uses). Asking too early is a {@link FailureKind#CONFLICT}, not
 * a not-found. Failures are reported as {@link LabRuleException}. Identifiers are
 * never logged.
 */
@Service
public class SampleQueryService {

    private final SampleRepository sampleRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final TestResultRepository testResultRepository;
    private final ReportRepository reportRepository;

    public SampleQueryService(SampleRepository sampleRepository,
                              ProcessingJobRepository processingJobRepository,
                              TestResultRepository testResultRepository,
                              ReportRepository reportRepository) {
        this.sampleRepository = sampleRepository;
        this.processingJobRepository = processingJobRepository;
        this.testResultRepository = testResultRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * Returns one sample with its job-derived status.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown
     */
    public SampleView get(String id) {
        return toView(findSample(id));
    }

    /**
     * Returns every registered sample (empty when there are none), oldest first
     * with ties broken by ID so the order is deterministic.
     */
    public List<SampleView> list() {
        return sampleRepository.findAll().stream()
                .sorted(Comparator.comparing(Sample::createdAt).thenComparing(Sample::id))
                .map(this::toView)
                .toList();
    }

    /**
     * Returns the stages the sample has passed through, oldest first and including
     * the current one, each with the time it was reached. A sample with no job has
     * a single entry: its stored status, at the time the sample was created.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown
     */
    public ProgressView progress(String id) {
        Sample sample = findSample(id);
        Optional<ProcessingJob> job = processingJobRepository.findBySampleId(sample.id());
        if (job.isEmpty()) {
            return new ProgressView(
                    sample.id(),
                    sample.status(),
                    List.of(new ProgressView.StageEntry(sample.status(), sample.createdAt())));
        }
        List<ProgressView.StageEntry> stages = job.get().history().stream()
                .map(change -> new ProgressView.StageEntry(change.status(), change.timestamp()))
                .toList();
        return new ProgressView(sample.id(), job.get().currentStatus(), stages);
    }

    /**
     * Returns the sample's test result, available once it has reached Result Ready
     * (and still available after the report is generated).
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown;
     *         {@code CONFLICT} when the sample has not reached Result Ready yet
     * @throws IllegalStateException if the sample is far enough along but has no stored
     *         result (a data-integrity bug, not a business rule)
     */
    public ResultView result(String id) {
        Sample sample = findSample(id);
        if (currentStatus(sample).ordinal() < SampleStatus.RESULT_READY.ordinal()) {
            throw new LabRuleException(FailureKind.CONFLICT, LabMessages.RESULT_NOT_READY);
        }
        TestResult testResult = findTestResult(sample);
        return new ResultView(sample.id(), testResult.testName(), testResult.resultValue());
    }

    /**
     * Returns the sample's report, available only once it has reached Report
     * Generated. Its content is the same the View Report screen shows.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown;
     *         {@code CONFLICT} when the sample has not reached Report Generated yet
     * @throws IllegalStateException if the sample is at Report Generated but has no
     *         stored result or report (a data-integrity bug, not a business rule)
     */
    public ReportView report(String id) {
        Sample sample = findSample(id);
        if (currentStatus(sample).ordinal() < SampleStatus.REPORT_GENERATED.ordinal()) {
            throw new LabRuleException(FailureKind.CONFLICT, LabMessages.REPORT_NOT_READY);
        }
        TestResult testResult = findTestResult(sample);
        Report report = reportRepository.findBySampleId(sample.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No Report found for sample at REPORT_GENERATED: " + sample.id()));
        return new ReportView(sample.id(), testResult.testName(), testResult.resultValue(), report.generatedAt());
    }

    private Sample findSample(String id) {
        if (id == null || id.isBlank()) {
            throw new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND);
        }
        return sampleRepository.findById(id)
                .orElseThrow(() -> new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND));
    }

    private SampleStatus currentStatus(Sample sample) {
        return processingJobRepository.findBySampleId(sample.id())
                .map(ProcessingJob::currentStatus)
                .orElse(sample.status());
    }

    private TestResult findTestResult(Sample sample) {
        return testResultRepository.findBySampleId(sample.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No TestResult found for sample at or past RESULT_READY: " + sample.id()));
    }

    private SampleView toView(Sample sample) {
        return new SampleView(
                sample.id(),
                sample.patientId(),
                sample.orderId(),
                sample.sampleType(),
                currentStatus(sample),
                sample.createdAt());
    }
}
