package com.labflow.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.TestResult;
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

    /**
     * {@code PUT /api/samples/{id}/status}: asks the lab to move the sample to another stage.
     *
     * @param body what to send as the JSON body: a record, a {@code Map}, or a raw
     *             {@code String} (sent as-is, for deliberately malformed JSON)
     */
    public LabResponse<Sample> moveTo(String id, Object body) {
        return LabResponse.from(specs.get().body(body).put(SAMPLES + "/{id}/status", id),
                "PUT", SAMPLES + "/" + id + "/status", Sample.class);
    }

    /** {@code GET /api/samples/{id}/progress}. */
    public LabResponse<Progress> progress(String id) {
        return LabResponse.from(specs.get().get(SAMPLES + "/{id}/progress", id),
                "GET", SAMPLES + "/" + id + "/progress", Progress.class);
    }

    /** {@code GET /api/samples/{id}/result}. */
    public LabResponse<TestResult> result(String id) {
        return LabResponse.from(specs.get().get(SAMPLES + "/{id}/result", id),
                "GET", SAMPLES + "/" + id + "/result", TestResult.class);
    }

    /**
     * {@code DELETE /api/samples/{id}}: a verb the lab does not support on samples, so it
     * answers 405 with an {@code Allow} header. The success type is {@code Void} because
     * there is no success body to read (it stays empty, and the error body is parsed as usual).
     */
    public LabResponse<Void> delete(String id) {
        return LabResponse.from(specs.get().delete(SAMPLES + "/{id}", id),
                "DELETE", SAMPLES + "/" + id, Void.class);
    }

    /** {@code GET /api/samples/{id}/report}. */
    public LabResponse<Report> report(String id) {
        return LabResponse.from(specs.get().get(SAMPLES + "/{id}/report", id),
                "GET", SAMPLES + "/" + id + "/report", Report.class);
    }
}
