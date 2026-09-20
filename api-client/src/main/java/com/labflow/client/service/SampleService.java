package com.labflow.client.service;

import com.labflow.client.api.SampleApi;
import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business verbs for samples. Each one expects the lab to accept the request and returns
 * the domain object; if the lab answers anything else it throws
 * {@link com.labflow.client.http.UnexpectedResponseError}, whose message names the request,
 * the status expected and the status (and lab message) received.
 */
public final class SampleService {

    private final SampleApi api;

    public SampleService(SampleApi api) {
        this.api = api;
    }

    /** Registers a sample for a patient without naming an order; the lab must answer 201. */
    public Sample register(String patientId, SampleType type) {
        return register(patientId, type, null);
    }

    /**
     * Registers a sample for a patient under an existing order; the lab must answer 201.
     * The request names the order only when {@code orderId} is not {@code null}.
     */
    public Sample register(String patientId, SampleType type, String orderId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("patientId", patientId);
        body.put("sampleType", type.name());
        if (orderId != null) {
            body.put("orderId", orderId);
        }
        return api.register(body).bodyOrFail(201);
    }

    /**
     * The "refusal expected" twin of {@link #register(String, SampleType)}: takes the sample
     * type as raw text (for example {@code "PLASMA"}, which the lab does not know), sends
     * whatever it is given and returns the lab's answer, whatever it is, without ever throwing
     * for a refusal. Any argument that is {@code null} is left out of the request.
     */
    public LabResponse<Sample> attemptRegister(String patientId, String rawType) {
        return attemptRegister(patientId, rawType, null);
    }

    /**
     * The "refusal expected" twin of {@link #register(String, SampleType, String)}; see
     * {@link #attemptRegister(String, String)}. The raw type is sent verbatim, and a
     * {@code null} patient id, type or order id is left out of the request.
     */
    public LabResponse<Sample> attemptRegister(String patientId, String rawType, String orderId) {
        Map<String, String> body = new LinkedHashMap<>();
        if (patientId != null) {
            body.put("patientId", patientId);
        }
        if (rawType != null) {
            body.put("sampleType", rawType);
        }
        if (orderId != null) {
            body.put("orderId", orderId);
        }
        return api.register(body);
    }

    /** Looks a sample up by the id the lab assigned; the lab must answer 200. */
    public Sample find(String id) {
        return api.find(id).bodyOrFail(200);
    }

    /**
     * The samples that belong to one patient. The lab only lists <em>all</em> samples
     * ({@code GET /api/samples}, which must answer 200), so this filters that list here.
     * Other checks may be running against the same lab, so a count or list of every sample
     * is deliberately not offered. The returned list cannot be modified.
     */
    public List<Sample> listFor(String patientId) {
        return api.list().bodyOrFail(200).stream()
                .filter(sample -> patientId.equals(sample.patientId()))
                .toList();
    }

    /**
     * Moves a sample to the given stage with one {@code PUT /api/samples/{id}/status}, which
     * carries the stage's constant name; the lab must answer 200 with the sample. The lab
     * only accepts the <em>next</em> stage, so this is the verb for a single step.
     */
    public Sample moveTo(String sampleId, WorkflowStage stage) {
        return api.moveTo(sampleId, Map.of("status", stage.name())).bodyOrFail(200);
    }

    /**
     * Walks a sample forward, one stage at a time, until it reaches {@code target}: it looks
     * the sample's current stage up, then sends one {@link #moveTo} per stage, following
     * {@link WorkflowStage#next()}. It returns the sample as the lab answered the last step.
     * If the lab refuses a step, the {@link com.labflow.client.http.UnexpectedResponseError}
     * of that step propagates and the sample stays where it got to.
     *
     * @throws IllegalArgumentException if {@code target} is not strictly after the sample's
     *                                  current stage; no move is sent in that case
     */
    public Sample walkTo(String sampleId, WorkflowStage target) {
        Sample sample = find(sampleId);
        WorkflowStage stage = sample.status();
        if (target.compareTo(stage) <= 0) {
            throw new IllegalArgumentException("Cannot walk sample " + sampleId + " to " + target
                    + ": it is already at " + stage + ", and a target must come after the current stage");
        }
        while (stage != target) {
            stage = stage.next().orElseThrow();
            sample = moveTo(sampleId, stage);
        }
        return sample;
    }

    /** The stages a sample has reached so far; the lab must answer 200. */
    public Progress progress(String id) {
        return api.progress(id).bodyOrFail(200);
    }

    /** A sample's test result (available from Result Ready on); the lab must answer 200. */
    public TestResult result(String id) {
        return api.result(id).bodyOrFail(200);
    }

    /** A sample's report (available at Report Generated); the lab must answer 200. */
    public Report report(String id) {
        return api.report(id).bodyOrFail(200);
    }
}
