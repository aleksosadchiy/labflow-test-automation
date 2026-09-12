package com.mocklab.service;

import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Advances a sample's {@link ProcessingJob} to the next stage of the lab
 * workflow, per functional-spec.md §2.4 and technical-considerations.md §6.
 *
 * <p>{@link #advance(String)} is the sole mutator of a job's
 * {@code currentStatus}/{@code history} and of the mirrored
 * {@link Sample#status()} — every transition is caused by exactly one
 * explicit call to it (no timer- or event-driven transitions).
 */
@Service
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final SampleRepository sampleRepository;

    public ProcessingJobService(ProcessingJobRepository processingJobRepository,
                                 SampleRepository sampleRepository) {
        this.processingJobRepository = processingJobRepository;
        this.sampleRepository = sampleRepository;
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
     * <p>Side effects on entering {@code RESULT_READY}/{@code
     * REPORT_GENERATED} (creating a {@code TestResult}/{@code Report}) are
     * out of scope here — a later slice wires them into this method.
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

        SampleStatus next = allStatuses[current.ordinal() + 1];
        Instant now = Instant.now();

        List<ProcessingJob.StatusChange> updatedHistory = new ArrayList<>(job.history());
        updatedHistory.add(new ProcessingJob.StatusChange(next, now));

        ProcessingJob updatedJob = processingJobRepository.save(new ProcessingJob(
                job.id(),
                job.sampleId(),
                next,
                List.copyOf(updatedHistory)));

        sampleRepository.findById(sampleId).ifPresent(sample -> sampleRepository.save(new Sample(
                sample.id(),
                sample.patientId(),
                sample.orderId(),
                sample.sampleType(),
                next,
                sample.createdAt())));

        return Optional.of(updatedJob);
    }
}
