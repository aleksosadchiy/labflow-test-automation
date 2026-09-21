package com.labflow.client.service;

import com.labflow.client.api.PatientApi;
import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Patient;

import java.util.LinkedHashMap;
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

    /**
     * The "refusal expected" twin of {@link #register}: sends whatever it is given and returns
     * the lab's answer, whatever it is, without ever throwing for a refusal. A {@code null}
     * name is left out of the request (the body is {@code {}}); a blank or any other string
     * is sent exactly as given.
     */
    public LabResponse<Patient> attemptRegister(String name) {
        Map<String, String> body = new LinkedHashMap<>();
        if (name != null) {
            body.put("name", name);
        }
        return api.register(body);
    }

    /**
     * The "refusal expected" twin of {@link #register} for content that is not a valid
     * request: sends {@code rawBody} to {@code POST /api/patients} exactly as given (for
     * example {@code not json at all}) and returns the lab's answer, never throwing.
     */
    public LabResponse<Patient> attemptRegisterRaw(String rawBody) {
        return api.register(rawBody);
    }

    /** Looks a patient up by the id the lab assigned; the lab must answer 200. */
    public Patient find(String id) {
        return api.find(id).bodyOrFail(200);
    }
}
