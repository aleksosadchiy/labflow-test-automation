package com.labflow.client.api;

import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Patient;
import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * The patient endpoints of the lab: one method per endpoint, no interpretation. It never
 * asserts and never throws for a "wrong" status; whatever the lab answers comes back as a
 * {@link LabResponse}. Business verbs live one layer up, in
 * {@link com.labflow.client.service.PatientService}.
 */
public final class PatientApi {

    private static final String PATIENTS = "/api/patients";

    private final Supplier<RequestSpecification> specs;

    /**
     * @param specs supplies a fresh request specification for every request (a
     *              RestAssured specification is mutable, so it is never reused)
     */
    public PatientApi(Supplier<RequestSpecification> specs) {
        this.specs = specs;
    }

    /**
     * {@code POST /api/patients}.
     *
     * @param body what to send as the JSON body: a record, a {@code Map}, or a raw
     *             {@code String} (sent as-is, for deliberately malformed JSON)
     */
    public LabResponse<Patient> register(Object body) {
        return LabResponse.from(specs.get().body(body).post(PATIENTS), "POST", PATIENTS, Patient.class);
    }

    /** {@code GET /api/patients/{id}}. */
    public LabResponse<Patient> find(String id) {
        return LabResponse.from(specs.get().get(PATIENTS + "/{id}", id), "GET", PATIENTS + "/" + id, Patient.class);
    }
}
