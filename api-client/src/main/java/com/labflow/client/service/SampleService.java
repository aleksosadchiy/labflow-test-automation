package com.labflow.client.service;

import com.labflow.client.api.SampleApi;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;

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
}
