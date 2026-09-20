package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.api.SampleApi;
import com.labflow.client.http.LabJson;
import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.http.LabResponse;
import com.labflow.client.http.UnexpectedResponseError;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the sample client layers ({@code SampleService}, {@code SampleApi}, reached
 * through {@link LabClient}), per functional-spec.md §2.2 (the lab layer does the calling
 * and fails clearly) and §2.5 (register a sample, look it up, list one patient's samples).
 * They run against a {@link StubLab}, so no real lab is needed and what was actually sent on
 * the wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class SampleServiceTest {

    private static final String KEY = "s3cret-sample-key-8842";
    private static final String PATIENT_ID = "p-4471";
    private static final String OTHER_PATIENT_ID = "p-9930";
    private static final String ORDER_ID = "o-1";
    private static final String CONFLICT_BODY = "{\"status\":409,\"error\":\"Conflict\","
            + "\"message\":\"A sample of this type already exists for this order.\","
            + "\"path\":\"/api/samples\",\"timestamp\":\"2026-01-05T10:15:30Z\"}";

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

    private static String sampleJson(String id, String patientId, String orderId, String type, String status) {
        return "{\"id\":\"" + id + "\",\"patientId\":\"" + patientId + "\",\"orderId\":\"" + orderId + "\","
                + "\"sampleType\":\"" + type + "\",\"status\":\"" + status + "\","
                + "\"createdAt\":\"2026-01-05T10:15:30Z\"}";
    }

    /** The lab's registration answer: the sample plus the nested order object. */
    private static String registrationJson(String id, String patientId, String orderId, String type) {
        String sample = sampleJson(id, patientId, orderId, type, "ACCESSIONED");
        return sample.substring(0, sample.length() - 1) + ",\"order\":{\"id\":\"" + orderId
                + "\",\"patientId\":\"" + patientId + "\",\"createdAt\":\"2026-01-05T10:00:00Z\"}}";
    }

    private static Sample sample(String id, String patientId, String orderId, SampleType type, WorkflowStage status) {
        return new Sample(id, patientId, orderId, type, status, Instant.parse("2026-01-05T10:15:30Z"));
    }

    private Map<String, String> sentBody() throws Exception {
        return LabJson.mapper().readValue(lab.lastRequest().body(), new TypeReference<Map<String, String>>() { });
    }

    // ---- register ----

    @Test
    // @regression
    @DisplayName("Registering a sample under an order sends patientId, sampleType and orderId as JSON to POST /api/samples")
    void registerUnderAnOrderSendsTheOrderId() throws Exception {
        lab.answer("POST", "/api/samples", 201, registrationJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD"));

        Sample sample = client.samples().register(PATIENT_ID, SampleType.BLOOD, ORDER_ID);

        assertThat(sample).isEqualTo(sample("s-1", PATIENT_ID, ORDER_ID, SampleType.BLOOD, WorkflowStage.ACCESSIONED));
        StubLab.ReceivedRequest sent = lab.lastRequest();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/api/samples");
        assertThat(sent.header("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "BLOOD", "orderId", ORDER_ID));
    }

    @Test
    // @regression
    @DisplayName("Registering a sample without an order sends no orderId at all, and the lab-created order's id is returned")
    void registerWithoutAnOrderSendsNoOrderId() throws Exception {
        lab.answer("POST", "/api/samples", 201, registrationJson("s-2", PATIENT_ID, "o-auto", "TISSUE"));

        Sample sample = client.samples().register(PATIENT_ID, SampleType.TISSUE);

        assertThat(sample).isEqualTo(sample("s-2", PATIENT_ID, "o-auto", SampleType.TISSUE, WorkflowStage.ACCESSIONED));
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "TISSUE"));
        assertThat(lab.lastRequest().body()).doesNotContain("orderId");
    }

    @Test
    // @regression
    @DisplayName("Registering a sample with a null order id is the same as naming no order")
    void registerWithANullOrderIdSendsNoOrderId() throws Exception {
        lab.answer("POST", "/api/samples", 201, registrationJson("s-2", PATIENT_ID, "o-auto", "SWAB"));

        client.samples().register(PATIENT_ID, SampleType.SWAB, null);

        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "SWAB"));
    }

    @Test
    // @regression
    @DisplayName("An unexpected status makes register throw an error that names the request, both statuses and the lab's message")
    void unexpectedStatusThrowsWithTheLabsMessage() {
        lab.answer("POST", "/api/samples", 409, CONFLICT_BODY, Map.of("X-Debug", "header-value-7703"));

        assertThatThrownBy(() -> client.samples().register(PATIENT_ID, SampleType.BLOOD, ORDER_ID))
                .isInstanceOf(UnexpectedResponseError.class)
                .isInstanceOf(AssertionError.class)
                .hasMessage("POST /api/samples expected 201 but the lab answered 409 "
                        + "\"A sample of this type already exists for this order.\"");
        assertThatThrownBy(() -> client.samples().register(PATIENT_ID, SampleType.BLOOD))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessageStartingWith("POST /api/samples expected 201 but the lab answered 409");
    }

    @Test
    // @regression
    @DisplayName("A 200 where 201 was expected is not accepted for register")
    void otherSuccessStatusIsNotTheExpectedOne() {
        lab.answer("POST", "/api/samples", 200, registrationJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD"));

        assertThatThrownBy(() -> client.samples().register(PATIENT_ID, SampleType.BLOOD))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("POST /api/samples expected 201 but the lab answered 200");
    }

    // ---- find ----

    @Test
    // @regression
    @DisplayName("Looking a sample up returns the lab's sample from GET /api/samples/{id}")
    void findReturnsTheSample() {
        lab.answer("GET", "/api/samples/s-1", 200, sampleJson("s-1", PATIENT_ID, ORDER_ID, "URINE", "PROCESSING"));

        Sample sample = client.samples().find("s-1");

        assertThat(sample).isEqualTo(sample("s-1", PATIENT_ID, ORDER_ID, SampleType.URINE, WorkflowStage.PROCESSING));
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples/s-1");
    }

    @Test
    // @regression
    @DisplayName("A failed lookup names the request, both statuses and the lab's message")
    void failedLookupThrowsWithTheLabsMessage() {
        lab.answer("GET", "/api/samples/nope", 404,
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Sample not found.\","
                        + "\"path\":\"/api/samples/nope\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        assertThatThrownBy(() -> client.samples().find("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples/nope expected 200 but the lab answered 404 \"Sample not found.\"");
    }

    // ---- listFor ----

    @Test
    // @regression
    @DisplayName("listFor returns only the given patient's samples from GET /api/samples, in the lab's order")
    void listForKeepsOnlyThatPatientsSamples() {
        lab.answer("GET", "/api/samples", 200, "["
                + sampleJson("s-1", PATIENT_ID, "o-1", "BLOOD", "ACCESSIONED") + ","
                + sampleJson("s-2", OTHER_PATIENT_ID, "o-2", "SWAB", "PROCESSING") + ","
                + sampleJson("s-3", PATIENT_ID, "o-1", "TISSUE", "READY_FOR_PROCESSING") + ","
                + sampleJson("s-4", OTHER_PATIENT_ID, "o-3", "URINE", "ACCESSIONED") + "]");

        List<Sample> samples = client.samples().listFor(PATIENT_ID);

        assertThat(samples).containsExactly(
                sample("s-1", PATIENT_ID, "o-1", SampleType.BLOOD, WorkflowStage.ACCESSIONED),
                sample("s-3", PATIENT_ID, "o-1", SampleType.TISSUE, WorkflowStage.READY_FOR_PROCESSING));
        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples");
    }

    @Test
    // @regression
    @DisplayName("listFor is empty when the patient has no samples, even if other patients do")
    void listForIsEmptyWhenThePatientHasNoSamples() {
        lab.answer("GET", "/api/samples", 200,
                "[" + sampleJson("s-2", OTHER_PATIENT_ID, "o-2", "SWAB", "PROCESSING") + "]");

        assertThat(client.samples().listFor(PATIENT_ID)).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("listFor is empty when the lab has no samples at all")
    void listForIsEmptyWhenTheLabIsEmpty() {
        lab.answer("GET", "/api/samples", 200, "[]");

        assertThat(client.samples().listFor(PATIENT_ID)).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("The list listFor returns cannot be modified")
    void listForReturnsAnImmutableList() {
        lab.answer("GET", "/api/samples", 200,
                "[" + sampleJson("s-1", PATIENT_ID, "o-1", "BLOOD", "ACCESSIONED") + "]");

        List<Sample> samples = client.samples().listFor(PATIENT_ID);

        assertThatThrownBy(() -> samples.add(sample("s-9", PATIENT_ID, "o-1", SampleType.SWAB, WorkflowStage.ACCESSIONED)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(samples::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    // @regression
    @DisplayName("A failed listing names the request, both statuses and the lab's message")
    void failedListingThrowsWithTheLabsMessage() {
        lab.answer("GET", "/api/samples", 401,
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid API key.\","
                        + "\"path\":\"/api/samples\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        assertThatThrownBy(() -> client.samples().listFor(PATIENT_ID))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/samples expected 200 but the lab answered 401 \"Missing or invalid API key.\"");
    }

    @Test
    // @regression
    @DisplayName("A 200 listing that is not a JSON array is an error, not an empty list")
    void listingThatIsNotAnArrayIsAnError() {
        lab.answer("GET", "/api/samples", 200, "{\"id\":\"s-1\"}");

        assertThatThrownBy(() -> client.samples().listFor(PATIENT_ID))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessageContaining("GET /api/samples expected 200 and the lab answered 200")
                .hasMessageContaining("list of Sample");
    }

    // ---- the key on the wire ----

    @Test
    // @regression
    @DisplayName("The configured key goes out as 'Authorization: Bearer <key>' on every sample call")
    void configuredKeyIsSentAsBearer() {
        lab.answer("POST", "/api/samples", 201, registrationJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD"));
        lab.answer("GET", "/api/samples/s-1", 200, sampleJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD", "ACCESSIONED"));
        lab.answer("GET", "/api/samples", 200, "[]");

        client.samples().register(PATIENT_ID, SampleType.BLOOD);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        client.samples().find("s-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        client.samples().listFor(PATIENT_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    @Test
    // @regression
    @DisplayName("withoutApiKey() sends no Authorization header on any sample call")
    void withoutApiKeySendsNoAuthorizationHeader() {
        lab.answer("POST", "/api/samples", 201, registrationJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD"));
        lab.answer("GET", "/api/samples/s-1", 200, sampleJson("s-1", PATIENT_ID, ORDER_ID, "BLOOD", "ACCESSIONED"));
        lab.answer("GET", "/api/samples", 200, "[]");
        LabClient anonymous = client.withoutApiKey();

        anonymous.samples().register(PATIENT_ID, SampleType.BLOOD);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        anonymous.samples().find("s-1");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        anonymous.samples().listFor(PATIENT_ID);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
    }

    @Test
    // @regression
    @DisplayName("withApiKey(\"wrong\") makes the sample calls send that key, and the original client is unchanged")
    void withApiKeySendsTheGivenKey() {
        lab.answer("GET", "/api/samples", 200, "[]");

        client.withApiKey("wrong").samples().listFor(PATIENT_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer wrong");

        client.samples().listFor(PATIENT_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    @Test
    // @regression
    @DisplayName("Failure messages contain no header values, no key and no patient id")
    void failureMessagesLeakNothing() {
        lab.answer("POST", "/api/samples", 409, CONFLICT_BODY, Map.of("X-Debug", "header-value-7703"));
        lab.answer("GET", "/api/samples", 500, "<html>oops</html>", Map.of("X-Debug", "header-value-7703"));

        assertThatThrownBy(() -> client.samples().register(PATIENT_ID, SampleType.BLOOD, ORDER_ID))
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("header-value-7703")
                .hasMessageNotContaining(PATIENT_ID)
                .hasMessageNotContaining(ORDER_ID);
        assertThatThrownBy(() -> client.samples().listFor(PATIENT_ID))
                .hasMessage("GET /api/samples expected 200 but the lab answered 500")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("header-value-7703")
                .hasMessageNotContaining(PATIENT_ID);
    }

    // ---- the Api layer ----

    @Test
    // @regression
    @DisplayName("The Api layer never asserts: a refused registration comes back as an ordinary response")
    void apiLayerReturnsRefusalsInsteadOfThrowing() {
        lab.answer("POST", "/api/samples", 409, CONFLICT_BODY);
        SampleApi api = new SampleApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<Sample> response = api.register(Map.of("patientId", PATIENT_ID, "sampleType", "BLOOD"));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEmpty();
        assertThat(response.describe())
                .isEqualTo("POST /api/samples -> 409 \"A sample of this type already exists for this order.\"");
    }

    @Test
    // @regression
    @DisplayName("The Api layer's list() returns the whole bare array, unfiltered, with the status and body readable")
    void apiLayerListReturnsEverySample() {
        lab.answer("GET", "/api/samples", 200, "["
                + sampleJson("s-1", PATIENT_ID, "o-1", "BLOOD", "ACCESSIONED") + ","
                + sampleJson("s-2", OTHER_PATIENT_ID, "o-2", "SWAB", "PROCESSING") + "]");
        SampleApi api = new SampleApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<List<Sample>> response = api.list();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.describe()).isEqualTo("GET /api/samples -> 200");
        assertThat(response.body()).hasValueSatisfying(samples -> assertThat(samples).hasSize(2));
    }

    @Test
    // @regression
    @DisplayName("A raw String body is sent to the lab exactly as given, even when it is not valid JSON")
    void rawStringBodyIsSentVerbatim() {
        lab.answer("POST", "/api/samples", 400,
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body.\","
                        + "\"path\":\"/api/samples\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");
        SampleApi api = new SampleApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<Sample> response = api.register("{\"patientId\": ");

        assertThat(lab.lastRequest().body()).isEqualTo("{\"patientId\": ");
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
