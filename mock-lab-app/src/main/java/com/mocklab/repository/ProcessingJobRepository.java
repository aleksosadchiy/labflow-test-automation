package com.mocklab.repository;

import com.mocklab.domain.ProcessingJob;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link ProcessingJob} records, keyed by job ID.
 * Also supports lookup by sample ID since the relationship is 1—1 and both
 * directions are needed by the sample workflow.
 * Nothing survives a JVM restart.
 */
@Repository
public class ProcessingJobRepository {

    private final ConcurrentHashMap<String, ProcessingJob> jobsById = new ConcurrentHashMap<>();

    public Optional<ProcessingJob> findById(String id) {
        return Optional.ofNullable(jobsById.get(id));
    }

    public Optional<ProcessingJob> findBySampleId(String sampleId) {
        return jobsById.values().stream()
                .filter(job -> job.sampleId().equals(sampleId))
                .findFirst();
    }

    public ProcessingJob save(ProcessingJob processingJob) {
        jobsById.put(processingJob.id(), processingJob);
        return processingJob;
    }
}
