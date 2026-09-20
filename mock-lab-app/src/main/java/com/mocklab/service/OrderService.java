package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Order;
import com.mocklab.domain.Sample;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Places and looks up {@link Order} records for the REST API, per
 * functional-spec.md §2.3 and technical-considerations.md §2.3.
 *
 * <p>Generated IDs are {@code ORD-<UUID>}. Failures are reported as
 * {@link LabRuleException} so the API layer can map them to the standard error
 * body. Patient identifiers are never logged.
 */
@Service
public class OrderService {

    private final PatientRepository patientRepository;
    private final OrderRepository orderRepository;
    private final SampleRepository sampleRepository;
    private final ProcessingJobRepository processingJobRepository;

    public OrderService(PatientRepository patientRepository,
                        OrderRepository orderRepository,
                        SampleRepository sampleRepository,
                        ProcessingJobRepository processingJobRepository) {
        this.patientRepository = patientRepository;
        this.orderRepository = orderRepository;
        this.sampleRepository = sampleRepository;
        this.processingJobRepository = processingJobRepository;
    }

    /**
     * Saves a new order for the given patient.
     *
     * @throws LabRuleException {@code VALIDATION} when {@code patientId} is null or blank
     *                          (checked first), {@code NOT_FOUND} when no such patient exists
     */
    public Order place(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            throw new LabRuleException(FailureKind.VALIDATION, LabMessages.PATIENT_REQUIRED);
        }
        if (patientRepository.findById(patientId).isEmpty()) {
            throw new LabRuleException(FailureKind.NOT_FOUND, LabMessages.PATIENT_NOT_FOUND);
        }
        return orderRepository.save(new Order("ORD-" + UUID.randomUUID(), patientId, Instant.now()));
    }

    /**
     * Returns the order and its samples (oldest first). Each sample's status is
     * read from its processing job, falling back to the sample's own stored
     * status only when it has no job.
     *
     * @throws LabRuleException {@code NOT_FOUND} when the ID is null, blank or unknown
     */
    public OrderDetails find(String id) {
        if (id == null || id.isBlank()) {
            throw new LabRuleException(FailureKind.NOT_FOUND, LabMessages.ORDER_NOT_FOUND);
        }
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new LabRuleException(FailureKind.NOT_FOUND, LabMessages.ORDER_NOT_FOUND));
        List<OrderDetails.SampleSummary> samples = sampleRepository.findByOrderId(order.id()).stream()
                .map(this::summarize)
                .toList();
        return new OrderDetails(order, samples);
    }

    private OrderDetails.SampleSummary summarize(Sample sample) {
        return new OrderDetails.SampleSummary(
                sample.id(),
                sample.sampleType(),
                processingJobRepository.findBySampleId(sample.id())
                        .map(job -> job.currentStatus())
                        .orElse(sample.status()));
    }
}
