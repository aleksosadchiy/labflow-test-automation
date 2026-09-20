package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a sample's {@link ProcessingJob} to the next stage of the lab
 * workflow, per functional-spec.md §2.4/§2.5 and technical-considerations.md
 * §2.5 and §6.
 *
 * <p>{@link #advance(String)} and {@link #advanceTo(String, SampleStatus)} are
 * the only mutators of a job's {@code currentStatus}/{@code history} and of
 * the mirrored {@link Sample#status()} — every transition is caused by exactly
 * one explicit call to one of them (no timer- or event-driven transitions).
 * Both share one private step ({@code moveTo}), so the history entry, the
 * sample mirror and the test-result/report side effects live in one place.
 *
 * <p>The two differ in what the caller is asking for:
 * <ul>
 *   <li>{@code advance} means "do the next step". It is not idempotent —
 *       calling it twice moves the sample two stages — and it quietly does
 *       nothing at the final stage. The screens' Advance button uses it.</li>
 *   <li>{@code advanceTo} means "request this specific next stage". It
 *       accepts only the stage immediately after the current one and rejects
 *       everything else (skip ahead, move back, same stage, any move from the
 *       final stage) with a conflict, so a retried request cannot move the
 *       sample any further. The status endpoint uses it.</li>
 * </ul>
 * Both are {@code synchronized} on this (singleton) service, so they exclude
 * each other as well as themselves.
 */
@Service
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final SampleRepository sampleRepository;
    private final ReportService reportService;

    public ProcessingJobService(ProcessingJobRepository processingJobRepository,
                                 SampleRepository sampleRepository,
                                 ReportService reportService) {
        this.processingJobRepository = processingJobRepository;
        this.sampleRepository = sampleRepository;
        this.reportService = reportService;
    }

    /**
     * Moves the {@link ProcessingJob} for {@code sampleId} to the next
     * {@link SampleStatus} in enum declaration order, appends one
     * {@link ProcessingJob.StatusChange} entry ({@code newStatus, now}) to
     * its history, persists the updated job, and updates the corresponding
     * {@link Sample#status()} to match. The whole read-modify-write is
     * {@code synchronized} on this (singleton) service instance so
     * concurrent callers can't race and lose an update.
     *
     * <p>Contract, for callers (e.g. the advance-stage controller):
     * <ul>
     *   <li>No {@link ProcessingJob} exists for {@code sampleId} →
     *       returns {@link Optional#empty()}. Treat as "sample/job not
     *       found" (e.g. respond 404).</li>
     *   <li>Job exists but is already at the final stage
     *       ({@link SampleStatus#REPORT_GENERATED}) → no-op (does not
     *       throw, does not advance past the end); returns
     *       {@link Optional#of} the job unchanged.</li>
     *   <li>Job exists and is not at the final stage → job is advanced by
     *       exactly one stage; returns {@link Optional#of} the updated
     *       job.</li>
     * </ul>
     *
     * <p>Entering {@link SampleStatus#RESULT_READY} or {@link
     * SampleStatus#REPORT_GENERATED} triggers a {@link ReportService} side
     * effect (creating the {@code TestResult}/{@code Report} respectively)
     * as part of this same synchronized call, so the side effect is
     * atomic with the status change.
     */
    public synchronized Optional<ProcessingJob> advance(String sampleId) {
        Optional<ProcessingJob> existing = processingJobRepository.findBySampleId(sampleId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        ProcessingJob job = existing.get();
        SampleStatus[] allStatuses = SampleStatus.values();
        SampleStatus current = job.currentStatus();

        if (current.ordinal() == allStatuses.length - 1) {
            return Optional.of(job);
        }

        return Optional.of(moveTo(job, allStatuses[current.ordinal() + 1]));
    }

    /**
     * Moves the {@link ProcessingJob} for {@code sampleId} to {@code target},
     * but only if {@code target} is the stage immediately after the job's
     * current one. Unlike {@link #advance(String)} this names the stage the
     * caller wants, so a repeated request is rejected instead of moving the
     * sample further. On success it behaves exactly like one
     * {@code advance} step (history entry, sample mirror, and the
     * test-result/report created on entering Result Ready / Report Generated),
     * and returns the updated job.
     *
     * <p>Failures, none of which change the job, the sample or create any
     * result/report:
     * <ul>
     *   <li>No job for {@code sampleId} (including a null or blank id) →
     *       {@link LabRuleException} {@link FailureKind#NOT_FOUND}.</li>
     *   <li>{@code target} is not the next stage — a skip ahead, a move
     *       back, the same stage, or any target once the sample is at
     *       {@link SampleStatus#REPORT_GENERATED} →
     *       {@link LabRuleException} {@link FailureKind#CONFLICT} with
     *       {@link LabMessages#invalidTransition(String, String)}.</li>
     * </ul>
     *
     * @throws NullPointerException if {@code target} is null; the caller
     *         validates the requested status first, so this is a programming
     *         error rather than a business rule
     */
    public synchronized ProcessingJob advanceTo(String sampleId, SampleStatus target) {
        Objects.requireNonNull(target, "target must not be null");

        ProcessingJob job = processingJobRepository.findBySampleId(sampleId)
                .orElseThrow(() -> new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND));

        SampleStatus current = job.currentStatus();
        if (target.ordinal() != current.ordinal() + 1) {
            throw new LabRuleException(
                    FailureKind.CONFLICT,
                    LabMessages.invalidTransition(current.displayName(), target.displayName()));
        }

        return moveTo(job, target);
    }

    /**
     * The one place a job changes stage: appends {@code {next, now}} to the
     * history, saves the job, mirrors the stage onto the {@link Sample}, and
     * fires the test-result/report side effect for the stage entered. The
     * caller has already decided the move is legal and holds the monitor.
     */
    private ProcessingJob moveTo(ProcessingJob job, SampleStatus next) {
        String sampleId = job.sampleId();
        Instant now = Instant.now();

        List<ProcessingJob.StatusChange> updatedHistory = new ArrayList<>(job.history());
        updatedHistory.add(new ProcessingJob.StatusChange(next, now));

        ProcessingJob updatedJob = processingJobRepository.save(new ProcessingJob(
                job.id(),
                sampleId,
                next,
                List.copyOf(updatedHistory)));

        sampleRepository.findById(sampleId).ifPresent(sample -> sampleRepository.save(new Sample(
                sample.id(),
                sample.patientId(),
                sample.orderId(),
                sample.sampleType(),
                next,
                sample.createdAt())));

        if (next == SampleStatus.RESULT_READY) {
            reportService.createTestResult(sampleId);
        } else if (next == SampleStatus.REPORT_GENERATED) {
            reportService.createReport(sampleId);
        }

        return updatedJob;
    }
}
