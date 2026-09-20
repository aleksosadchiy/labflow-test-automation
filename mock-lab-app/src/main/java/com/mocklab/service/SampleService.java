package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Order;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.exception.FailureKind;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates and creates a new {@link Sample} together with its backing
 * {@link Order} and {@link ProcessingJob}, per functional-spec.md §2.3
 * ("Register a New Sample") and technical-considerations.md §6.
 *
 * <p>{@link #createSample(String, String, String)} takes raw {@code String}
 * parameters — exactly what a web form or JSON body submits — and resolves
 * {@code sampleType} against {@link SampleType} case-insensitively inside
 * this method. The two-argument {@link #createSample(String, String)} (used by
 * the screens) delegates with no order named. Validation is applied in this
 * exact order, stopping at the first failure; rules 5-7 apply only when a
 * non-blank {@code orderId} is supplied:
 * <ol>
 *   <li>{@code patientId} blank → {@code VALIDATION}, {@code "Patient is required."}</li>
 *   <li>{@code sampleType} blank → {@code VALIDATION}, {@code "Sample type is required."}</li>
 *   <li>{@code patientId} not found in {@link PatientRepository} → {@code NOT_FOUND}, {@code "Patient not found."}</li>
 *   <li>{@code sampleType} not a known {@link SampleType} (case-insensitive match attempted first) → {@code VALIDATION}, {@code "Invalid sample type."}</li>
 *   <li>{@code orderId} not found in {@link OrderRepository} → {@code NOT_FOUND}, {@code "Order not found."}</li>
 *   <li>the order belongs to a different patient than {@code patientId} → {@code CONFLICT}, {@code "Order does not belong to this patient."}</li>
 *   <li>the order already has a sample of this type → {@code CONFLICT}, {@code "A sample of this type already exists for this order."}</li>
 *   <li>generated sample ID already present in {@link SampleRepository} (defensive; practically unreachable with UUID-based IDs) → {@code CONFLICT}, {@code "A sample with this ID already exists."}</li>
 * </ol>
 *
 * <p>Each failure carries a {@link FailureKind} alongside its message so the
 * REST API (a later task) can map it to an HTTP status; the screens keep using
 * the message only.
 *
 * <p>On success, a {@link Sample} with {@code status = ACCESSIONED}, and a
 * {@link ProcessingJob} with {@code currentStatus = ACCESSIONED} and one
 * history entry are created and persisted together. If no order was named, a
 * new {@link Order} (linked to the patient) is created too; if an existing
 * order was named, the sample is attached to that order and no new order is
 * created (an order may hold several samples, one per type). See
 * {@link SampleCreationResult} for the exact contract callers receive.
 */
@Service
public class SampleService {

    private final PatientRepository patientRepository;
    private final OrderRepository orderRepository;
    private final SampleRepository sampleRepository;
    private final ProcessingJobRepository processingJobRepository;

    public SampleService(PatientRepository patientRepository,
                          OrderRepository orderRepository,
                          SampleRepository sampleRepository,
                          ProcessingJobRepository processingJobRepository) {
        this.patientRepository = patientRepository;
        this.orderRepository = orderRepository;
        this.sampleRepository = sampleRepository;
        this.processingJobRepository = processingJobRepository;
    }

    /** Registers a sample under a brand-new order (what the screens do). */
    public SampleCreationResult createSample(String patientId, String sampleType) {
        return createSample(patientId, sampleType, null);
    }

    /**
     * Registers a sample, optionally under an existing order. A {@code null} or
     * blank {@code orderId} means "no order named": a new order is created.
     *
     * <p>{@code synchronized} because the "one sample per type per order" rule is
     * check-then-act: without the lock, two simultaneous requests could both see
     * "no sample of this type yet" and both save one. A single global lock is fine
     * at this scale and keeps unrelated registrations correct too.
     */
    public synchronized SampleCreationResult createSample(String patientId, String sampleType, String orderId) {
        if (patientId == null || patientId.isBlank()) {
            return new SampleCreationResult.Failure(FailureKind.VALIDATION, "Patient is required.");
        }
        if (sampleType == null || sampleType.isBlank()) {
            return new SampleCreationResult.Failure(FailureKind.VALIDATION, "Sample type is required.");
        }
        if (patientRepository.findById(patientId).isEmpty()) {
            return new SampleCreationResult.Failure(FailureKind.NOT_FOUND, "Patient not found.");
        }

        Optional<SampleType> resolvedType = resolveSampleType(sampleType);
        if (resolvedType.isEmpty()) {
            return new SampleCreationResult.Failure(FailureKind.VALIDATION, "Invalid sample type.");
        }

        Order existingOrder = null;
        if (orderId != null && !orderId.isBlank()) {
            Optional<Order> found = orderRepository.findById(orderId);
            if (found.isEmpty()) {
                return new SampleCreationResult.Failure(FailureKind.NOT_FOUND, LabMessages.ORDER_NOT_FOUND);
            }
            existingOrder = found.get();
            if (!existingOrder.patientId().equals(patientId)) {
                return new SampleCreationResult.Failure(FailureKind.CONFLICT, LabMessages.ORDER_PATIENT_MISMATCH);
            }
            SampleType requestedType = resolvedType.get();
            boolean typeAlreadyOnOrder = sampleRepository.findByOrderId(existingOrder.id()).stream()
                    .anyMatch(existing -> existing.sampleType() == requestedType);
            if (typeAlreadyOnOrder) {
                return new SampleCreationResult.Failure(FailureKind.CONFLICT, LabMessages.DUPLICATE_SAMPLE_TYPE);
            }
        }

        String sampleId = generateId("SMP");
        if (sampleRepository.findById(sampleId).isPresent()) {
            return new SampleCreationResult.Failure(FailureKind.CONFLICT, "A sample with this ID already exists.");
        }

        Instant now = Instant.now();

        Order order = existingOrder != null
                ? existingOrder
                : orderRepository.save(new Order(generateId("ORD"), patientId, now));

        Sample sample = sampleRepository.save(new Sample(
                sampleId,
                patientId,
                order.id(),
                resolvedType.get(),
                SampleStatus.ACCESSIONED,
                now));

        ProcessingJob processingJob = processingJobRepository.save(new ProcessingJob(
                generateId("JOB"),
                sampleId,
                SampleStatus.ACCESSIONED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.ACCESSIONED, now))));

        return new SampleCreationResult.Success(order, sample, processingJob);
    }

    private Optional<SampleType> resolveSampleType(String sampleType) {
        String trimmed = sampleType.trim();
        return Arrays.stream(SampleType.values())
                .filter(type -> type.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
