package com.mocklab.service;

import com.mocklab.domain.Order;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.exception.FailureKind;

/**
 * Outcome of {@link SampleService#createSample(String, String)}.
 *
 * <p>A sealed result type rather than an exception: the caller (the
 * Create-Sample controller, a later task) is expected to always
 * pattern-match on it and treat "validation failed" as a normal, expected
 * outcome — not exceptional control flow — then re-render the form with
 * {@link Failure#message()} as the single relevant error message. The
 * {@link Failure#kind()} says which category the failure is (validation, not
 * found, conflict); the screens ignore it, and the REST API uses it to pick
 * the HTTP status.
 *
 * <pre>{@code
 * SampleCreationResult result = sampleService.createSample(patientId, sampleType);
 * if (result instanceof SampleCreationResult.Success success) {
 *     // redirect to the new sample, e.g. success.sample().id()
 * } else if (result instanceof SampleCreationResult.Failure failure) {
 *     // re-render the form with failure.message()
 * }
 * }</pre>
 */
public sealed interface SampleCreationResult {

    /**
     * Validation passed; the {@link Order}, {@link Sample}
     * (status {@code ACCESSIONED}) and {@link ProcessingJob}
     * (current status {@code ACCESSIONED}, one history entry) have all
     * been persisted and are tied together via
     * {@code sample.orderId() == order.id()} and
     * {@code processingJob.sampleId() == sample.id()}.
     */
    record Success(Order order, Sample sample, ProcessingJob processingJob) implements SampleCreationResult {
    }

    /**
     * Validation failed at the first-violated rule in the precedence order
     * documented on {@link SampleService#createSample(String, String)}.
     * {@code message} is the exact, user-facing string from the functional
     * spec (e.g. {@code "Patient is required."}) — safe to display verbatim.
     * {@code kind} is the {@link FailureKind} category of that rule.
     */
    record Failure(FailureKind kind, String message) implements SampleCreationResult {
    }
}
