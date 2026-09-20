package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Sample;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Read side of samples for the REST API, per functional-spec.md §2.4 and
 * technical-considerations.md §2.2-2.3.
 *
 * <p>Each sample's status is read from its processing job (the source of truth),
 * falling back to the sample's own stored status only when it has no job.
 * Failures are reported as {@link LabRuleException}. Identifiers are never logged.
 */
@Service
public class SampleQueryService {

    private final SampleRepository sampleRepository;
    private final ProcessingJobRepository processingJobRepository;

    public SampleQueryService(SampleRepository sampleRepository,
                              ProcessingJobRepository processingJobRepository) {
        this.sampleRepository = sampleRepository;
        this.processingJobRepository = processingJobRepository;
    }

    /**
     * Returns one sample with its job-derived status.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown
     */
    public SampleView get(String id) {
        if (id == null || id.isBlank()) {
            throw new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND);
        }
        Sample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND));
        return toView(sample);
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

    private SampleView toView(Sample sample) {
        return new SampleView(
                sample.id(),
                sample.patientId(),
                sample.orderId(),
                sample.sampleType(),
                processingJobRepository.findBySampleId(sample.id())
                        .map(job -> job.currentStatus())
                        .orElse(sample.status()),
                sample.createdAt());
    }
}
