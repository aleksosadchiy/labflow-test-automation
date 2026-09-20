package com.labflow.client.service;

import com.labflow.client.api.PatientApi;
import com.labflow.common.model.Patient;

import java.util.Map;

/**
 * Business verbs for patients. Each one expects the lab to accept the request and returns
 * the domain object; if the lab answers anything else it throws
 * {@link com.labflow.client.http.UnexpectedResponseError}, whose message names the request,
 * the status expected and the status (and lab message) received.
 */
public final class PatientService {

    private final PatientApi api;

    public PatientService(PatientApi api) {
        this.api = api;
    }

    /** Registers a patient; the lab must answer 201. */
    public Patient register(String name) {
        return api.register(Map.of("name", name)).bodyOrFail(201);
    }

    /** Looks a patient up by the id the lab assigned; the lab must answer 200. */
    public Patient find(String id) {
        return api.find(id).bodyOrFail(200);
    }
}
