package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.client.api.SampleApi;
import com.labflow.client.http.LabJson;
import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.http.LabResponse;
import com.labflow.client.http.UnexpectedResponseError;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.config.LabEnvironment;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.StageEntry;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for the client layers that move a sample through the lab's workflow and read what it
 * produced ({@code SampleService.moveTo / walkTo / progress / result / report} and the
 * matching {@code SampleApi} methods), per functional-spec.md §2.4 (typed answers) and §2.5
 * (stages one at a time, result, report). They run against a {@link StubLab}; the walking
 * checks use a stub sample that, like the real lab, accepts only the <em>next</em> stage, so
 * a walk that skips or repeats a stage is refused and what was sent is inspected in order.
 *
 * @spec: 003-test-automation-foundation
 */
class SampleWorkflowServiceTest {

    private static final String KEY = "w4lk-sample-key-5519";
    private static final String SAMPLE_ID = "s-1";
    private static final String PATIENT_ID = "p-4471";
    private static final String ORDER_ID = "o-1";
    private static final String SAMPLE_PATH = "/api/samples/" + SAMPLE_ID;
    private static final String STATUS_PATH = SAMPLE_PATH + "/status";
    private static final String NOT_FOUND_BODY = "{\"status\":404,\"error\":\"Not Found\","
            + "\"message\":\"Sample not found.\",\"path\":\"/api/samples/nope\","
            + "\"timestamp\":\"2026-01-05T10:15:30Z\"}";

    private StubLab lab;
    private LabClient client;

    @BeforeEach
    void startLab() {
        lab = StubLab.start();
        client = LabClient.create(new LabConfig(lab.baseUrl(), new ApiKey(KEY), LabEnvironment.LOCAL));
    }

    @AfterEach
    void stopLab() {
        lab.close();
    }

    private static String sampleJson(WorkflowStage status) {
        return "{\"id\":\"" + SAMPLE_ID + "\",\"patientId\":\"" + PATIENT_ID + "\",\"orderId\":\"" + ORDER_ID + "\","
                + "\"sampleType\":\"BLOOD\",\"status\":\"" + status.name() + "\","
                + "\"createdAt\":\"2026-01-05T10:15:30Z\"}";
    }

    private static Sample sample(WorkflowStage status) {
        return new Sample(SAMPLE_ID, PATIENT_ID, ORDER_ID, SampleType.BLOOD, status,
                Instant.parse("2026-01-05T10:15:30Z"));
    }

    private static StubLab.Answer json(int status, String body) {
        return new StubLab.Answer(status, body, Map.of());
    }

    private static String errorJson(int status, String error, String message) {
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\","
                + "\"path\":\"" + STATUS_PATH + "\",\"timestamp\":\"2026-01-05T10:15:30Z\"}";
    }

    /** The {@code status} the request body asks for, or an empty string if there is none. */
    private static String requestedStatus(String body) {
        try {
            return LabJson.mapper().readTree(body).path("status").asText("");
        } catch (Exception notJson) {
            return "";
        }
    }

    /**
     * Makes the stub behave like the lab for one sample that starts at {@code start}: GET
     * answers the sample where it is now, and PUT accepts only the next stage's name (moving
     * the sample there) and answers 409 to anything else. If {@code refuseAt} is not
     * {@code null}, a PUT for that stage is refused with 409 even though it is the next one.
     */
    private void labWithSampleAt(WorkflowStage start, WorkflowStage refuseAt) {
        AtomicReference<WorkflowStage> current = new AtomicReference<>(start);
        lab.respond("GET", SAMPLE_PATH, request -> json(200, sampleJson(current.get())));
        lab.respond("PUT", STATUS_PATH, request -> {
            String asked = requestedStatus(request.body());
            boolean isNext = current.get().next().map(WorkflowStage::name).filter(asked::equals).isPresent();
            if (isNext && WorkflowStage.valueOf(asked) == refuseAt) {
                return json(409, errorJson(409, "Conflict", "The lab refused this step."));
            }
            if (!isNext) {
                return json(409, errorJson(409, "Conflict", "The sample can only move to the next stage."));
            }
            current.set(WorkflowStage.valueOf(asked));
            return json(200, sampleJson(current.get()));
        });
    }

    private List<StubLab.ReceivedRequest> puts() {
        return lab.requests().stream().filter(request -> request.method().equals("PUT")).toList();
    }

    private static String statusBody(WorkflowStage stage) {
        return "{\"status\":\"" + stage.name() + "\"}";
    }

    // ---- moveTo ----

    @Test
    // @regression
    @DisplayName("moveTo sends PUT /api/samples/{id}/status with the stage's name as JSON and returns the lab's sample")
    void moveToSendsThePutAndReturnsTheSample() {
        labWithSampleAt(WorkflowStage.ACCESSIONED, null);

        Sample moved = client.samples().moveTo(SAMPLE_ID, WorkflowStage.READY_FOR_PROCESSING);

        assertThat(moved).isEqualTo(sample(WorkflowStage.READY_FOR_PROCESSING));
        assertThat(lab.requests()).hasSize(1);
        StubLab.ReceivedRequest sent = lab.lastRequest();
        assertThat(sent.method()).isEqualTo("PUT");
        assertThat(sent.path()).isEqualTo("/api/samples/s-1/status");
        assertThat(sent.header("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(sent.body()).isEqualTo("{\"status\":\"READY_FOR_PROCESSING\"}");
    }

    @ParameterizedTest
    // @regression
    @EnumSource(WorkflowStage.class)
    @DisplayName("moveTo names the stage by its constant name (the wire value), never by its display name")
    void moveToSendsTheConstantName(WorkflowStage stage) {
        lab.answer("PUT", STATUS_PATH, 200, sampleJson(stage));

        client.samples().moveTo(SAMPLE_ID, stage);

        assertThat(lab.lastRequest().body()).isEqualTo("{\"status\":\"" + stage.name() + "\"}");
    }

    @Test
    // @regression
    @DisplayName("A refused move makes moveTo throw an error that names the request, both statuses and the lab's message")
    void refusedMoveThrowsWithTheLabsMessage() {
        lab.answer("PUT", STATUS_PATH, 409, errorJson(409, "Conflict",
                "Sample must move to the next stage: Ready for Processing."), Map.of("X-Debug", "header-value-6612"));

        assertThatThrownBy(() -> client.samples().moveTo(SAMPLE_ID, WorkflowStage.SEQUENCING))
                .isInstanceOf(UnexpectedResponseError.class)
                .isInstanceOf(AssertionError.class)
                .hasMessage("PUT /api/samples/s-1/status expected 200 but the lab answered 409 "
                        + "\"Sample must move to the next stage: Ready for Processing.\"")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("header-value-6612");
    }

    @Test
    // @regression
    @DisplayName("A 201 where 200 was expected is not accepted for moveTo")
    void otherSuccessStatusIsNotAcceptedForMoveTo() {
        lab.answer("PUT", STATUS_PATH, 201, sampleJson(WorkflowStage.READY_FOR_PROCESSING));

        assertThatThrownBy(() -> client.samples().moveTo(SAMPLE_ID, WorkflowStage.READY_FOR_PROCESSING))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("PUT /api/samples/s-1/status expected 200 but the lab answered 201");
    }

    // ---- walkTo ----

    @Test
    // @regression
    @DisplayName("walkTo from ACCESSIONED to REPORT_GENERATED reads the sample once, then sends exactly seven PUTs in workflow order")
    void walkToTheEndSendsSevenPutsInOrder() {
        labWithSampleAt(WorkflowStage.ACCESSIONED, null);

        Sample last = client.samples().walkTo(SAMPLE_ID, WorkflowStage.REPORT_GENERATED);

        assertThat(last).isEqualTo(sample(WorkflowStage.REPORT_GENERATED));
        assertThat(lab.requests()).extracting(StubLab.ReceivedRequest::method, StubLab.ReceivedRequest::path)
                .containsExactly(
                        tuple("GET", SAMPLE_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH),
                        tuple("PUT", STATUS_PATH));
        assertThat(puts()).extracting(StubLab.ReceivedRequest::body).containsExactly(
                statusBody(WorkflowStage.READY_FOR_PROCESSING),
                statusBody(WorkflowStage.PROCESSING),
                statusBody(WorkflowStage.SEQUENCING),
                statusBody(WorkflowStage.BIOINFORMATICS),
                statusBody(WorkflowStage.QUALITY_CHECK),
                statusBody(WorkflowStage.RESULT_READY),
                statusBody(WorkflowStage.REPORT_GENERATED));
    }

    @Test
    // @regression
    @DisplayName("walkTo an intermediate stage stops there: two PUTs to reach PROCESSING, and no more")
    void walkToAnIntermediateStageStopsThere() {
        labWithSampleAt(WorkflowStage.ACCESSIONED, null);

        Sample last = client.samples().walkTo(SAMPLE_ID, WorkflowStage.PROCESSING);

        assertThat(last).isEqualTo(sample(WorkflowStage.PROCESSING));
        assertThat(puts()).extracting(StubLab.ReceivedRequest::body).containsExactly(
                statusBody(WorkflowStage.READY_FOR_PROCESSING),
                statusBody(WorkflowStage.PROCESSING));
        assertThat(lab.requests()).hasSize(3);
    }

    @Test
    // @regression
    @DisplayName("walkTo starts from the sample's current stage, not from the beginning")
    void walkToStartsFromTheCurrentStage() {
        labWithSampleAt(WorkflowStage.PROCESSING, null);

        Sample last = client.samples().walkTo(SAMPLE_ID, WorkflowStage.QUALITY_CHECK);

        assertThat(last).isEqualTo(sample(WorkflowStage.QUALITY_CHECK));
        assertThat(puts()).extracting(StubLab.ReceivedRequest::body).containsExactly(
                statusBody(WorkflowStage.SEQUENCING),
                statusBody(WorkflowStage.BIOINFORMATICS),
                statusBody(WorkflowStage.QUALITY_CHECK));
    }

    @Test
    // @regression
    @DisplayName("walkTo the very next stage sends a single PUT")
    void walkToTheNextStageSendsOnePut() {
        labWithSampleAt(WorkflowStage.RESULT_READY, null);

        Sample last = client.samples().walkTo(SAMPLE_ID, WorkflowStage.REPORT_GENERATED);

        assertThat(last).isEqualTo(sample(WorkflowStage.REPORT_GENERATED));
        assertThat(puts()).extracting(StubLab.ReceivedRequest::body)
                .containsExactly(statusBody(WorkflowStage.REPORT_GENERATED));
    }

    @ParameterizedTest
    // @regression
    @CsvSource({
            "PROCESSING, PROCESSING",
            "PROCESSING, READY_FOR_PROCESSING",
            "PROCESSING, ACCESSIONED",
            "ACCESSIONED, ACCESSIONED",
            "REPORT_GENERATED, REPORT_GENERATED",
            "REPORT_GENERATED, ACCESSIONED"})
    @DisplayName("walkTo the current stage or an earlier one throws IllegalArgumentException and sends no PUT")
    void walkToAStageThatIsNotAheadIsRefusedWithoutARequest(WorkflowStage current, WorkflowStage target) {
        labWithSampleAt(current, null);

        assertThatThrownBy(() -> client.samples().walkTo(SAMPLE_ID, target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SAMPLE_ID)
                .hasMessageContaining(target.name())
                .hasMessageContaining("already at " + current.name())
                .hasMessageContaining("must come after the current stage")
                .hasMessageNotContaining(KEY);
        assertThat(puts()).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("An unexpected 409 mid-walk propagates as UnexpectedResponseError naming the refused PUT, and the walk stops there")
    void aRefusedStepMidWalkPropagates() {
        labWithSampleAt(WorkflowStage.ACCESSIONED, WorkflowStage.PROCESSING);

        assertThatThrownBy(() -> client.samples().walkTo(SAMPLE_ID, WorkflowStage.REPORT_GENERATED))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("PUT /api/samples/s-1/status expected 200 but the lab answered 409 "
                        + "\"The lab refused this step.\"");
        assertThat(puts()).extracting(StubLab.ReceivedRequest::body).containsExactly(
                statusBody(WorkflowStage.READY_FOR_PROCESSING),
                statusBody(WorkflowStage.PROCESSING));
    }

    @Test
    // @regression
    @DisplayName("walkTo an unknown sample fails on the lookup with the lab's message, before any PUT")
    void walkToAnUnknownSampleFailsOnTheLookup() {
        lab.answer("GET", "/api/samples/nope", 404, NOT_FOUND_BODY);

        assertThatThrownBy(() -> client.samples().walkTo("nope", WorkflowStage.PROCESSING))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples/nope expected 200 but the lab answered 404 \"Sample not found.\"");
        assertThat(puts()).isEmpty();
    }

    // ---- progress, result, report ----

    @Test
    // @regression
    @DisplayName("progress returns the typed stage list from GET /api/samples/{id}/progress")
    void progressIsTyped() {
        lab.answer("GET", SAMPLE_PATH + "/progress", 200, "{\"sampleId\":\"s-1\",\"currentStatus\":\"PROCESSING\","
                + "\"stages\":[{\"status\":\"ACCESSIONED\",\"reachedAt\":\"2026-01-05T10:15:30Z\"},"
                + "{\"status\":\"READY_FOR_PROCESSING\",\"reachedAt\":\"2026-01-05T10:16:00Z\"},"
                + "{\"status\":\"PROCESSING\",\"reachedAt\":\"2026-01-05T10:17:00.250Z\"}]}");

        Progress progress = client.samples().progress(SAMPLE_ID);

        assertThat(progress).isEqualTo(new Progress(SAMPLE_ID, WorkflowStage.PROCESSING, List.of(
                new StageEntry(WorkflowStage.ACCESSIONED, Instant.parse("2026-01-05T10:15:30Z")),
                new StageEntry(WorkflowStage.READY_FOR_PROCESSING, Instant.parse("2026-01-05T10:16:00Z")),
                new StageEntry(WorkflowStage.PROCESSING, Instant.parse("2026-01-05T10:17:00.250Z")))));
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples/s-1/progress");
    }

    @Test
    // @regression
    @DisplayName("result returns the typed test result from GET /api/samples/{id}/result")
    void resultIsTyped() {
        lab.answer("GET", SAMPLE_PATH + "/result", 200,
                "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\",\"resultValue\":\"13.5 g/dL\",\"lab\":\"north\"}");

        TestResult result = client.samples().result(SAMPLE_ID);

        assertThat(result).isEqualTo(new TestResult(SAMPLE_ID, "Hemoglobin", "13.5 g/dL"));
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples/s-1/result");
    }

    @Test
    // @regression
    @DisplayName("report returns the typed report from GET /api/samples/{id}/report")
    void reportIsTyped() {
        lab.answer("GET", SAMPLE_PATH + "/report", 200, "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\","
                + "\"resultValue\":\"13.5 g/dL\",\"generatedAt\":\"2026-01-05T11:00:00Z\"}");

        Report report = client.samples().report(SAMPLE_ID);

        assertThat(report).isEqualTo(new Report(SAMPLE_ID, "Hemoglobin", "13.5 g/dL",
                Instant.parse("2026-01-05T11:00:00Z")));
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples/s-1/report");
    }

    @Test
    // @regression
    @DisplayName("A refused progress, result or report names the request, both statuses and the lab's message")
    void refusedReadsThrowWithTheLabsMessage() {
        lab.answer("GET", "/api/samples/nope/progress", 404, NOT_FOUND_BODY);
        lab.answer("GET", "/api/samples/nope/result", 404, NOT_FOUND_BODY);
        lab.answer("GET", "/api/samples/nope/report", 404, NOT_FOUND_BODY);

        assertThatThrownBy(() -> client.samples().progress("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples/nope/progress expected 200 but the lab answered 404 \"Sample not found.\"");
        assertThatThrownBy(() -> client.samples().result("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples/nope/result expected 200 but the lab answered 404 \"Sample not found.\"");
        assertThatThrownBy(() -> client.samples().report("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples/nope/report expected 200 but the lab answered 404 \"Sample not found.\"");
    }

    // ---- the key on the wire ----

    private void answerEverything() {
        labWithSampleAt(WorkflowStage.ACCESSIONED, null);
        lab.answer("GET", SAMPLE_PATH + "/progress", 200,
                "{\"sampleId\":\"s-1\",\"currentStatus\":\"ACCESSIONED\",\"stages\":[]}");
        lab.answer("GET", SAMPLE_PATH + "/result", 200,
                "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\",\"resultValue\":\"13.5 g/dL\"}");
        lab.answer("GET", SAMPLE_PATH + "/report", 200, "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\","
                + "\"resultValue\":\"13.5 g/dL\",\"generatedAt\":\"2026-01-05T11:00:00Z\"}");
    }

    private void useEveryVerb(LabClient labClient) {
        labClient.samples().moveTo(SAMPLE_ID, WorkflowStage.READY_FOR_PROCESSING);
        labClient.samples().walkTo(SAMPLE_ID, WorkflowStage.SEQUENCING);
        labClient.samples().progress(SAMPLE_ID);
        labClient.samples().result(SAMPLE_ID);
        labClient.samples().report(SAMPLE_ID);
    }

    @Test
    // @regression
    @DisplayName("The configured key goes out as 'Authorization: Bearer <key>' on every move, walk and read")
    void configuredKeyIsSentAsBearer() {
        answerEverything();

        useEveryVerb(client);

        // moveTo (1) + walkTo (1 GET + 2 PUTs) + progress + result + report
        assertThat(lab.requests()).hasSize(7);
        assertThat(lab.requests()).allSatisfy(request ->
                assertThat(request.header("Authorization")).contains("Bearer " + KEY));
    }

    @Test
    // @regression
    @DisplayName("withoutApiKey() sends no Authorization header on any move, walk or read")
    void withoutApiKeySendsNoAuthorizationHeader() {
        answerEverything();

        useEveryVerb(client.withoutApiKey());

        assertThat(lab.requests()).hasSize(7);
        assertThat(lab.requests()).allSatisfy(request ->
                assertThat(request.headers()).doesNotContainKey("Authorization"));
    }

    @Test
    // @regression
    @DisplayName("Failure messages of moves and reads contain no header values and no key")
    void failureMessagesLeakNothing() {
        lab.answer("PUT", STATUS_PATH, 500, "<html>oops</html>", Map.of("X-Debug", "header-value-6612"));
        lab.answer("GET", SAMPLE_PATH + "/report", 500, "<html>oops</html>", Map.of("X-Debug", "header-value-6612"));

        assertThatThrownBy(() -> client.samples().moveTo(SAMPLE_ID, WorkflowStage.READY_FOR_PROCESSING))
                .hasMessage("PUT /api/samples/s-1/status expected 200 but the lab answered 500")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("header-value-6612");
        assertThatThrownBy(() -> client.samples().report(SAMPLE_ID))
                .hasMessage("GET /api/samples/s-1/report expected 200 but the lab answered 500")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("header-value-6612");
    }

    // ---- the Api layer ----

    @Test
    // @regression
    @DisplayName("The Api layer never asserts: a refused move comes back as an ordinary response, and a raw String body is sent verbatim")
    void apiLayerReturnsRefusalsInsteadOfThrowing() {
        lab.answer("PUT", STATUS_PATH, 400, errorJson(400, "Bad Request", "Malformed request body."));
        SampleApi api = new SampleApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<Sample> response = api.moveTo(SAMPLE_ID, "{\"status\": ");

        assertThat(lab.lastRequest().method()).isEqualTo("PUT");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples/s-1/status");
        assertThat(lab.lastRequest().body()).isEqualTo("{\"status\": ");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEmpty();
        assertThat(response.describe()).isEqualTo("PUT /api/samples/s-1/status -> 400 \"Malformed request body.\"");
    }

    @Test
    // @regression
    @DisplayName("The Api layer's progress, result and report return typed responses with the status readable")
    void apiLayerReadsAreTyped() {
        lab.answer("GET", SAMPLE_PATH + "/progress", 200,
                "{\"sampleId\":\"s-1\",\"currentStatus\":\"ACCESSIONED\",\"stages\":[]}");
        lab.answer("GET", SAMPLE_PATH + "/result", 409, errorJson(409, "Conflict", "Result is not ready yet."));
        lab.answer("GET", SAMPLE_PATH + "/report", 200, "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\","
                + "\"resultValue\":\"13.5 g/dL\",\"generatedAt\":\"2026-01-05T11:00:00Z\"}");
        SampleApi api = new SampleApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        assertThat(api.progress(SAMPLE_ID).describe()).isEqualTo("GET /api/samples/s-1/progress -> 200");
        assertThat(api.progress(SAMPLE_ID).body()).hasValueSatisfying(progress ->
                assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.ACCESSIONED));
        LabResponse<TestResult> notReady = api.result(SAMPLE_ID);
        assertThat(notReady.statusCode()).isEqualTo(409);
        assertThat(notReady.body()).isEmpty();
        assertThat(notReady.describe()).isEqualTo("GET /api/samples/s-1/result -> 409 \"Result is not ready yet.\"");
        assertThat(api.report(SAMPLE_ID).body()).hasValueSatisfying(report ->
                assertThat(report.generatedAt()).isEqualTo(Instant.parse("2026-01-05T11:00:00Z")));
    }
}
