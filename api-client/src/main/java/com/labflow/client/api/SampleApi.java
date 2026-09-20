package com.labflow.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Sample;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.function.Supplier;

/**
 * The sample endpoints of the lab: one method per endpoint, no interpretation. It never
 * asserts and never throws for a "wrong" status; whatever the lab answers comes back as a
 * {@link LabResponse}. Business verbs live one layer up, in
 * {@link com.labflow.client.service.SampleService}.
 */
public final class SampleApi {

    private static final String SAMPLES = "/api/samples";

    private final Supplier<RequestSpecification> specs;

    /**
     * @param specs supplies a fresh request specification for every request (a
     *              RestAssured specification is mutable, so it is never reused)
     */
    public SampleApi(Supplier<RequestSpecification> specs) {
        this.specs = specs;
    }

    /**
     * {@code POST /api/samples}.
     *
     * @param body what to send as the JSON body: a record, a {@code Map}, or a raw
     *             {@code String} (sent as-is, for deliberately malformed JSON)
     */
    public LabResponse<Sample> register(Object body) {
        return LabResponse.from(specs.get().body(body).post(SAMPLES), "POST", SAMPLES, Sample.class);
    }

    /** {@code GET /api/samples/{id}}. */
    public LabResponse<Sample> find(String id) {
        return LabResponse.from(specs.get().get(SAMPLES + "/{id}", id), "GET", SAMPLES + "/" + id, Sample.class);
    }

    /** {@code GET /api/samples}: every sample in the lab, as a bare JSON array. */
    public LabResponse<List<Sample>> list() {
        return LabResponse.from(specs.get().get(SAMPLES), "GET", SAMPLES, new TypeReference<List<Sample>>() { });
    }
}
