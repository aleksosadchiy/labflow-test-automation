package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.http.LabJson;
import com.labflow.client.http.LabResponse;
import com.labflow.client.support.StubLab;
import com.labflow.common.LabMessages;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@code attempt…} twins of {@code SampleService}'s workflow verbs
 * ({@code attemptFind}, {@code attemptMoveTo} in both forms, {@code attemptProgress},
 * {@code attemptResult}, {@code attemptReport}), per functional-spec.md §2.6: a check that
 * expects a refusal sends raw input and gets the lab's answer back, never an exception.
 * They run against a {@link StubLab}, so what was actually sent on the wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class AttemptSampleWorkflowServiceTest {

    private static final String KEY = "s3cret-attempt-workflow-key-2291";
    private static final String SAMPLE_ID = "SMP-1";
    private static final String SAMPLE_PATH = "/api/samples/" + SAMPLE_ID;
    private static final String SAMPLE_JSON = "{\"id\":\"SMP-1\",\"patientId\":\"p-4471\",\"orderId\":\"o-1\","
            + "\"sampleType\":\"SWAB\",\"status\":\"PROCESSING\",\"createdAt\":\"2026-01-05T10:15:30Z\"}";

    private StubLab lab;
    private LabClient client;

    @BeforeEach
    void startLab() {
        lab = StubLab.start();
        client = LabClient.create(new LabConfig(lab.baseUrl(), new ApiKey(KEY)));
    }

    @AfterEach
    void stopLab() {
        lab.close();
    }

    private static String errorBody(int status, String error, String message, String path) {
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\","
                + "\"path\":\"" + path + "\",\"timestamp\":\"2026-01-05T10:15:30Z\"}";
    }

    private Map<String, String> sentBody() throws Exception {
        return LabJson.mapper().readValue(lab.lastRequest().body(), new TypeReference<Map<String, String>>() { });
    }

    // ---- attemptFind ----

    @Test
    // @regression
    @DisplayName("attemptFind sends GET /api/samples/{id} with the key and returns the typed sample when the lab accepts")
    void attemptFindSendsAGetAndParsesTheSample() {
        lab.answer("GET", SAMPLE_PATH, 200, SAMPLE_JSON);

        LabResponse<Sample> response = client.samples().attemptFind(SAMPLE_ID);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasValueSatisfying(sample -> {
            assertThat(sample.id()).isEqualTo(SAMPLE_ID);
            assertThat(sample.sampleType()).isEqualTo(SampleType.SWAB);
            assertThat(sample.status()).isEqualTo(WorkflowStage.PROCESSING);
        });
    }

    @Test
    // @regression
    @DisplayName("attemptFind for an unknown sample returns the 404, not an exception, with the lab's error readable")
    void attemptFindReturnsTheRefusal() {
        lab.answer("GET", "/api/samples/nope", 404,
                errorBody(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND, "/api/samples/nope"));

        LabResponse<Sample> response = client.samples().attemptFind("nope");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Sample not found.");
        assertThat(response.error()).hasValueSatisfying(error -> {
            assertThat(error.status()).isEqualTo(404);
            assertThat(error.error()).isEqualTo("Not Found");
            assertThat(error.message()).isEqualTo("Sample not found.");
            assertThat(error.path()).isEqualTo("/api/samples/nope");
            assertThat(error.timestamp()).isEqualTo(Instant.parse("2026-01-05T10:15:30Z"));
        });
        assertThat(response.describe()).isEqualTo("GET /api/samples/nope -> 404 \"Sample not found.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptFind sends no Authorization header after withoutApiKey(), and returns the 401")
    void attemptFindWithoutAKeyIsReturnedToo() {
        lab.answer("GET", SAMPLE_PATH, 401, errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, SAMPLE_PATH));

        LabResponse<Sample> response = client.withoutApiKey().samples().attemptFind(SAMPLE_ID);

        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.message()).isEqualTo("Unauthorized.");
    }

    // ---- attemptMoveTo (raw text) ----

    @Test
    // @regression
    @DisplayName("attemptMoveTo(raw) sends PUT /api/samples/{id}/status with exactly {\"status\": raw} and the key")
    void attemptMoveToSendsAPutWithTheStatus() throws Exception {
        lab.answer("PUT", SAMPLE_PATH + "/status", 200, SAMPLE_JSON);

        client.samples().attemptMoveTo(SAMPLE_ID, "SEQUENCING");

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("PUT");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH + "/status");
        assertThat(lab.lastRequest().body()).isEqualTo("{\"status\":\"SEQUENCING\"}");
        assertThat(sentBody()).isEqualTo(Map.of("status", "SEQUENCING"));
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    @Test
    // @regression
    @DisplayName("attemptMoveTo(raw) sends the status verbatim: garbage, other casing and padding are not fixed up")
    void attemptMoveToSendsTheRawStatusVerbatim() throws Exception {
        lab.answer("PUT", SAMPLE_PATH + "/status", 400,
                errorBody(400, "Bad Request", LabMessages.INVALID_STATUS, SAMPLE_PATH + "/status"));

        client.samples().attemptMoveTo(SAMPLE_ID, "not-a-stage");
        assertThat(sentBody()).isEqualTo(Map.of("status", "not-a-stage"));

        client.samples().attemptMoveTo(SAMPLE_ID, "processing");
        assertThat(sentBody()).isEqualTo(Map.of("status", "processing"));

        client.samples().attemptMoveTo(SAMPLE_ID, " PROCESSING ");
        assertThat(sentBody()).isEqualTo(Map.of("status", " PROCESSING "));

        client.samples().attemptMoveTo(SAMPLE_ID, "");
        assertThat(sentBody()).isEqualTo(Map.of("status", ""));

        client.samples().attemptMoveTo(SAMPLE_ID, "Ready for Processing");
        assertThat(sentBody()).isEqualTo(Map.of("status", "Ready for Processing"));
    }

    @Test
    // @regression
    @DisplayName("attemptMoveTo(null) sends an empty JSON object, with no status field and no \"null\" text")
    void attemptMoveToWithANullStatusOmitsTheField() {
        lab.answer("PUT", SAMPLE_PATH + "/status", 400,
                errorBody(400, "Bad Request", LabMessages.STATUS_REQUIRED, SAMPLE_PATH + "/status"));

        LabResponse<Sample> response = client.samples().attemptMoveTo(SAMPLE_ID, (String) null);

        assertThat(lab.lastRequest().body()).isEqualTo("{}");
        assertThat(lab.lastRequest().body()).doesNotContain("null").doesNotContain("status");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Status is required.");
    }

    @Test
    // @regression
    @DisplayName("attemptMoveTo(stage) sends the stage's constant name, for every stage")
    void attemptMoveToAStageSendsTheConstantName() throws Exception {
        lab.answer("PUT", SAMPLE_PATH + "/status", 409,
                errorBody(409, "Conflict", LabMessages.invalidTransition(WorkflowStage.PROCESSING,
                        WorkflowStage.SEQUENCING), SAMPLE_PATH + "/status"));

        for (WorkflowStage stage : WorkflowStage.values()) {
            client.samples().attemptMoveTo(SAMPLE_ID, stage);

            assertThat(lab.lastRequest().method()).isEqualTo("PUT");
            assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH + "/status");
            assertThat(sentBody()).isEqualTo(Map.of("status", stage.name()));
            assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        }
        assertThat(lab.requests()).hasSize(WorkflowStage.values().length);
    }

    @Test
    // @regression
    @DisplayName("A refused move (409 wrong stage) is returned, not thrown, with the lab's error and a describe() line")
    void aRefusedMoveIsReturnedNotThrown() {
        String message = LabMessages.invalidTransition(WorkflowStage.PROCESSING, WorkflowStage.QUALITY_CHECK);
        lab.answer("PUT", SAMPLE_PATH + "/status", 409, errorBody(409, "Conflict", message, SAMPLE_PATH + "/status"));

        LabResponse<Sample> byStage = client.samples().attemptMoveTo(SAMPLE_ID, WorkflowStage.QUALITY_CHECK);
        LabResponse<Sample> byText = client.samples().attemptMoveTo(SAMPLE_ID, "QUALITY_CHECK");

        for (LabResponse<Sample> response : List.of(byStage, byText)) {
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).isEmpty();
            assertThat(response.message())
                    .isEqualTo("Invalid status transition: cannot move from Processing to Quality Check.");
            assertThat(response.error()).hasValueSatisfying(error -> {
                assertThat(error.status()).isEqualTo(409);
                assertThat(error.error()).isEqualTo("Conflict");
                assertThat(error.path()).isEqualTo(SAMPLE_PATH + "/status");
            });
            assertThat(response.describe()).isEqualTo("PUT /api/samples/SMP-1/status -> 409 \""
                    + "Invalid status transition: cannot move from Processing to Quality Check.\"");
        }
    }

    @Test
    // @regression
    @DisplayName("A move to an unknown sample (404) and a garbage status (400) are returned, not thrown")
    void moveRefusalsOfEveryKindAreReturned() {
        lab.answer("PUT", "/api/samples/nope/status", 404,
                errorBody(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND, "/api/samples/nope/status"));
        LabResponse<Sample> notFound = client.samples().attemptMoveTo("nope", WorkflowStage.READY_FOR_PROCESSING);
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(notFound.message()).isEqualTo("Sample not found.");
        assertThat(notFound.describe()).isEqualTo("PUT /api/samples/nope/status -> 404 \"Sample not found.\"");

        lab.answer("PUT", SAMPLE_PATH + "/status", 400,
                errorBody(400, "Bad Request", LabMessages.INVALID_STATUS, SAMPLE_PATH + "/status"));
        LabResponse<Sample> garbage = client.samples().attemptMoveTo(SAMPLE_ID, "GARBAGE");
        assertThat(garbage.statusCode()).isEqualTo(400);
        assertThat(garbage.message()).isEqualTo("Invalid status.");
        assertThat(garbage.body()).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("An accepted attemptMoveTo returns the typed sample, in both forms, so a check can assert an acceptance")
    void anAcceptedMoveReturnsTheTypedSample() {
        lab.answer("PUT", SAMPLE_PATH + "/status", 200, SAMPLE_JSON);

        LabResponse<Sample> byStage = client.samples().attemptMoveTo(SAMPLE_ID, WorkflowStage.PROCESSING);
        LabResponse<Sample> byText = client.samples().attemptMoveTo(SAMPLE_ID, "PROCESSING");

        for (LabResponse<Sample> response : List.of(byStage, byText)) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.error()).isEmpty();
            assertThat(response.body()).hasValueSatisfying(sample -> {
                assertThat(sample.id()).isEqualTo(SAMPLE_ID);
                assertThat(sample.status()).isEqualTo(WorkflowStage.PROCESSING);
            });
            assertThat(response.describe()).isEqualTo("PUT /api/samples/SMP-1/status -> 200");
        }
    }

    @Test
    // @regression
    @DisplayName("attemptMoveTo (both forms) sends no Authorization header after withoutApiKey(), and returns the 401")
    void attemptMoveToWithoutAKeyIsReturnedToo() {
        lab.answer("PUT", SAMPLE_PATH + "/status", 401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, SAMPLE_PATH + "/status"));
        LabClient anonymous = client.withoutApiKey();

        LabResponse<Sample> byText = anonymous.samples().attemptMoveTo(SAMPLE_ID, "SEQUENCING");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        LabResponse<Sample> byStage = anonymous.samples().attemptMoveTo(SAMPLE_ID, WorkflowStage.SEQUENCING);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");

        assertThat(byText.statusCode()).isEqualTo(401);
        assertThat(byStage.message()).isEqualTo("Unauthorized.");
    }

    // ---- attemptProgress ----

    @Test
    // @regression
    @DisplayName("attemptProgress sends GET /api/samples/{id}/progress with the key and returns the typed progress")
    void attemptProgressSendsAGetAndParsesTheProgress() {
        lab.answer("GET", SAMPLE_PATH + "/progress", 200, "{\"sampleId\":\"SMP-1\",\"currentStatus\":\"READY_FOR_PROCESSING\","
                + "\"stages\":[{\"status\":\"ACCESSIONED\",\"reachedAt\":\"2026-01-05T10:15:30Z\"},"
                + "{\"status\":\"READY_FOR_PROCESSING\",\"reachedAt\":\"2026-01-05T10:16:00Z\"}]}");

        LabResponse<Progress> response = client.samples().attemptProgress(SAMPLE_ID);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH + "/progress");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasValueSatisfying(progress -> {
            assertThat(progress.sampleId()).isEqualTo(SAMPLE_ID);
            assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.READY_FOR_PROCESSING);
            assertThat(progress.stages()).extracting(entry -> entry.status())
                    .containsExactly(WorkflowStage.ACCESSIONED, WorkflowStage.READY_FOR_PROCESSING);
        });
    }

    @Test
    // @regression
    @DisplayName("attemptProgress for an unknown sample returns the 404, not an exception, with the lab's error readable")
    void attemptProgressReturnsTheRefusal() {
        lab.answer("GET", "/api/samples/nope/progress", 404,
                errorBody(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND, "/api/samples/nope/progress"));

        LabResponse<Progress> response = client.samples().attemptProgress("nope");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Sample not found.");
        assertThat(response.error()).hasValueSatisfying(error -> assertThat(error.path()).isEqualTo("/api/samples/nope/progress"));
        assertThat(response.describe()).isEqualTo("GET /api/samples/nope/progress -> 404 \"Sample not found.\"");
    }

    // ---- attemptResult ----

    @Test
    // @regression
    @DisplayName("attemptResult sends GET /api/samples/{id}/result with the key and returns the typed result")
    void attemptResultSendsAGetAndParsesTheResult() {
        lab.answer("GET", SAMPLE_PATH + "/result", 200,
                "{\"sampleId\":\"SMP-1\",\"testName\":\"Hemoglobin\",\"resultValue\":\"13.5 g/dL\"}");

        LabResponse<TestResult> response = client.samples().attemptResult(SAMPLE_ID);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH + "/result");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(new TestResult(SAMPLE_ID, "Hemoglobin", "13.5 g/dL"));
    }

    @Test
    // @regression
    @DisplayName("attemptResult before the result is ready returns the 409, not an exception, with the lab's error readable")
    void attemptResultReturnsTheRefusal() {
        lab.answer("GET", SAMPLE_PATH + "/result", 409,
                errorBody(409, "Conflict", LabMessages.RESULT_NOT_READY, SAMPLE_PATH + "/result"));

        LabResponse<TestResult> response = client.samples().attemptResult(SAMPLE_ID);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Test result is not ready yet.");
        assertThat(response.error()).hasValueSatisfying(error -> assertThat(error.error()).isEqualTo("Conflict"));
        assertThat(response.describe())
                .isEqualTo("GET /api/samples/SMP-1/result -> 409 \"Test result is not ready yet.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptResult for an unknown sample returns the 404 with the lab's message")
    void attemptResultForAnUnknownSampleIsReturned() {
        lab.answer("GET", "/api/samples/nope/result", 404,
                errorBody(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND, "/api/samples/nope/result"));

        LabResponse<TestResult> response = client.samples().attemptResult("nope");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("Sample not found.");
    }

    // ---- attemptReport ----

    @Test
    // @regression
    @DisplayName("attemptReport sends GET /api/samples/{id}/report with the key and returns the typed report")
    void attemptReportSendsAGetAndParsesTheReport() {
        lab.answer("GET", SAMPLE_PATH + "/report", 200, "{\"sampleId\":\"SMP-1\",\"testName\":\"Hemoglobin\","
                + "\"resultValue\":\"13.5 g/dL\",\"generatedAt\":\"2026-01-05T11:00:00Z\"}");

        LabResponse<Report> response = client.samples().attemptReport(SAMPLE_ID);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH + "/report");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                new Report(SAMPLE_ID, "Hemoglobin", "13.5 g/dL", Instant.parse("2026-01-05T11:00:00Z")));
    }

    @Test
    // @regression
    @DisplayName("attemptReport before the report is ready returns the 409, not an exception, with the lab's error readable")
    void attemptReportReturnsTheRefusal() {
        lab.answer("GET", SAMPLE_PATH + "/report", 409,
                errorBody(409, "Conflict", LabMessages.REPORT_NOT_READY, SAMPLE_PATH + "/report"));

        LabResponse<Report> response = client.samples().attemptReport(SAMPLE_ID);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Report is not ready yet.");
        assertThat(response.error()).hasValueSatisfying(error -> assertThat(error.path()).isEqualTo(SAMPLE_PATH + "/report"));
        assertThat(response.describe())
                .isEqualTo("GET /api/samples/SMP-1/report -> 409 \"Report is not ready yet.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptReport for an unknown sample returns the 404 with the lab's message")
    void attemptReportForAnUnknownSampleIsReturned() {
        lab.answer("GET", "/api/samples/nope/report", 404,
                errorBody(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND, "/api/samples/nope/report"));

        LabResponse<Report> response = client.samples().attemptReport("nope");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("Sample not found.");
        assertThat(response.describe()).isEqualTo("GET /api/samples/nope/report -> 404 \"Sample not found.\"");
    }

    // ---- what a returned response never carries ----

    @Test
    // @regression
    @DisplayName("The describe() line of a returned response never contains the key, headers or the request body")
    void describeNamesTheRequestOnly() {
        lab.answer("PUT", SAMPLE_PATH + "/status", 409, errorBody(409, "Conflict", "Nope.", SAMPLE_PATH + "/status"),
                Map.of("X-Trace", "trace-value-77"));

        String line = client.samples().attemptMoveTo(SAMPLE_ID, "SEQUENCING").describe();

        assertThat(line).isEqualTo("PUT /api/samples/SMP-1/status -> 409 \"Nope.\"");
        assertThat(line).doesNotContain(KEY).doesNotContain("Bearer").doesNotContain("trace-value-77")
                .doesNotContain("SEQUENCING");
    }
}
