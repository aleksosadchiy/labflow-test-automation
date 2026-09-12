package com.mocklab.service;

import com.mocklab.domain.Report;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.TestResult;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.TestResultRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates the {@link TestResult}/{@link Report} side effects that
 * {@code ProcessingJobService#advance(String)} triggers on specific
 * transitions, per technical-considerations.md §6:
 * <ul>
 *   <li>Entering {@link SampleStatus#RESULT_READY} →
 *       {@link #createTestResult(String)} creates the {@link TestResult}
 *       with a fixed/deterministic placeholder {@code testName}/
 *       {@code resultValue} (no real bioinformatics, per the project's
 *       non-goals).</li>
 *   <li>Entering {@link SampleStatus#REPORT_GENERATED} →
 *       {@link #createReport(String)} creates the {@link Report} with
 *       {@code generatedAt = now}.</li>
 * </ul>
 */
@Service
public class ReportService {

    private static final String PLACEHOLDER_TEST_NAME = "Genomic Panel";
    private static final String PLACEHOLDER_RESULT_VALUE = "Normal";

    private final TestResultRepository testResultRepository;
    private final ReportRepository reportRepository;

    public ReportService(TestResultRepository testResultRepository, ReportRepository reportRepository) {
        this.testResultRepository = testResultRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * Creates and persists a {@link TestResult} for {@code sampleId} with a
     * fixed placeholder {@code testName}/{@code resultValue} and
     * {@code generatedAt = now}.
     */
    public TestResult createTestResult(String sampleId) {
        TestResult testResult = new TestResult(
                generateId("RES"),
                sampleId,
                PLACEHOLDER_TEST_NAME,
                PLACEHOLDER_RESULT_VALUE,
                Instant.now());
        return testResultRepository.save(testResult);
    }

    /**
     * Creates and persists a {@link Report} for {@code sampleId} with
     * {@code generatedAt = now}.
     */
    public Report createReport(String sampleId) {
        Report report = new Report(generateId("RPT"), sampleId, Instant.now());
        return reportRepository.save(report);
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
