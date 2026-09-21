package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.api.OrderApi;
import com.labflow.client.http.LabJson;
import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.http.LabResponse;
import com.labflow.client.http.UnexpectedResponseError;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Order;
import com.labflow.common.model.SampleSummary;
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
 * Tests for the order client layers ({@code OrderService}, {@code OrderApi}, reached through
 * {@link LabClient}), per functional-spec.md §2.2 (the lab layer does the calling and fails
 * clearly) and §2.5 (place an order and look it up). They run against a {@link StubLab}, so
 * no real lab is needed and what was actually sent on the wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class OrderServiceTest {

    private static final String KEY = "s3cret-order-key-7731";
    private static final String PATIENT_ID = "p-4471";
    private static final String ORDER_JSON = "{\"id\":\"o-1\",\"patientId\":\"" + PATIENT_ID + "\","
            + "\"createdAt\":\"2026-01-05T10:15:30Z\","
            + "\"samples\":[{\"id\":\"s-1\",\"sampleType\":\"BLOOD\",\"status\":\"ACCESSIONED\"}]}";
    private static final Order ORDER = new Order("o-1", PATIENT_ID, Instant.parse("2026-01-05T10:15:30Z"),
            List.of(new SampleSummary("s-1", SampleType.BLOOD, WorkflowStage.ACCESSIONED)));
    private static final String PATIENT_NOT_FOUND_BODY = "{\"status\":404,\"error\":\"Not Found\","
            + "\"message\":\"Patient not found.\",\"path\":\"/api/orders\","
            + "\"timestamp\":\"2026-01-05T10:15:30Z\"}";

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

    // ---- what the service returns and sends ----

    @Test
    // @regression
    @DisplayName("Placing an order returns the lab's order and sends only the patient id as JSON to POST /api/orders")
    void placeReturnsTheOrderAndSendsOnlyThePatientId() throws Exception {
        lab.answer("POST", "/api/orders", 201, ORDER_JSON);

        Order order = client.orders().place(PATIENT_ID);

        assertThat(order).isEqualTo(ORDER);
        StubLab.ReceivedRequest sent = lab.lastRequest();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/api/orders");
        assertThat(sent.header("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(LabJson.mapper().readValue(sent.body(), new TypeReference<Map<String, String>>() { }))
                .isEqualTo(Map.of("patientId", PATIENT_ID));
    }

    @Test
    // @regression
    @DisplayName("Looking an order up returns the lab's order, samples included, from GET /api/orders/{id}")
    void findReturnsTheOrder() {
        lab.answer("GET", "/api/orders/o-1", 200, ORDER_JSON);

        Order order = client.orders().find("o-1");

        assertThat(order).isEqualTo(ORDER);
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/orders/o-1");
    }

    // ---- the key on the wire ----

    @Test
    // @regression
    @DisplayName("The configured key goes out as 'Authorization: Bearer <key>' on both order calls")
    void configuredKeyIsSentAsBearer() {
        lab.answer("POST", "/api/orders", 201, ORDER_JSON);
        lab.answer("GET", "/api/orders/o-1", 200, ORDER_JSON);

        client.orders().place(PATIENT_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        client.orders().find("o-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    @Test
    // @regression
    @DisplayName("withoutApiKey() sends no Authorization header on the order calls")
    void withoutApiKeySendsNoAuthorizationHeader() {
        lab.answer("POST", "/api/orders", 201, ORDER_JSON);
        lab.answer("GET", "/api/orders/o-1", 200, ORDER_JSON);

        client.withoutApiKey().orders().place(PATIENT_ID);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        client.withoutApiKey().orders().find("o-1");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
    }

    @Test
    // @regression
    @DisplayName("withApiKey(\"wrong\") makes the order calls send that key, and the original client is unchanged")
    void withApiKeySendsTheGivenKey() {
        lab.answer("GET", "/api/orders/o-1", 200, ORDER_JSON);

        client.withApiKey("wrong").orders().find("o-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer wrong");

        client.orders().find("o-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    // ---- an unexpected answer fails clearly ----

    @Test
    // @regression
    @DisplayName("An unexpected status makes place throw an error that names the request, both statuses and the lab's message")
    void unexpectedStatusThrowsWithTheLabsMessage() {
        lab.answer("POST", "/api/orders", 404, PATIENT_NOT_FOUND_BODY, Map.of("X-Debug", "header-value-5521"));

        assertThatThrownBy(() -> client.orders().place(PATIENT_ID))
                .isInstanceOf(UnexpectedResponseError.class)
                .isInstanceOf(AssertionError.class)
                .hasMessage("POST /api/orders expected 201 but the lab answered 404 \"Patient not found.\"")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("header-value-5521")
                .hasMessageNotContaining(PATIENT_ID);
    }

    @Test
    // @regression
    @DisplayName("A failed lookup names the request, both statuses and the lab's message")
    void failedLookupThrowsWithTheLabsMessage() {
        lab.answer("GET", "/api/orders/nope", 404,
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Order not found.\","
                        + "\"path\":\"/api/orders/nope\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        assertThatThrownBy(() -> client.orders().find("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/orders/nope expected 200 but the lab answered 404 \"Order not found.\"");
    }

    @Test
    // @regression
    @DisplayName("A 200 where 201 was expected is not accepted for place")
    void otherSuccessStatusIsNotTheExpectedOne() {
        lab.answer("POST", "/api/orders", 200, ORDER_JSON);

        assertThatThrownBy(() -> client.orders().place(PATIENT_ID))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("POST /api/orders expected 201 but the lab answered 200");
    }

    // ---- the Api layer ----

    @Test
    // @regression
    @DisplayName("The Api layer never asserts: a refused placement comes back as an ordinary response")
    void apiLayerReturnsRefusalsInsteadOfThrowing() {
        lab.answer("POST", "/api/orders", 404, PATIENT_NOT_FOUND_BODY);
        OrderApi api = new OrderApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<Order> response = api.place(Map.of("patientId", "unknown"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Patient not found.");
        assertThat(response.describe()).isEqualTo("POST /api/orders -> 404 \"Patient not found.\"");
    }

    @Test
    // @regression
    @DisplayName("A raw String body is sent to the lab exactly as given, even when it is not valid JSON")
    void rawStringBodyIsSentVerbatim() {
        lab.answer("POST", "/api/orders", 400,
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body.\","
                        + "\"path\":\"/api/orders\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");
        OrderApi api = new OrderApi(() -> LabRequestSpecs.create(lab.baseUrl(), new ApiKey(KEY)));

        LabResponse<Order> response = api.place("{\"patientId\": ");

        assertThat(lab.lastRequest().body()).isEqualTo("{\"patientId\": ");
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
