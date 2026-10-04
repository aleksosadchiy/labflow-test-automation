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
import com.labflow.common.config.LabEnvironment;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@code attempt…} twins of the client's services
 * ({@code PatientService.attemptRegister}, {@code OrderService.attemptPlace},
 * {@code SampleService.attemptRegister}), per functional-spec.md §2.6: a check that expects
 * a refusal sends raw input and gets the lab's answer back, never an exception. They run
 * against a {@link StubLab}, so what was actually sent on the wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class AttemptServicesTest {

    private static final String KEY = "s3cret-attempt-key-6634";
    private static final String PATIENT_ID = "p-4471";
    private static final String ORDER_ID = "o-1";

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

    private static String errorBody(int status, String error, String message, String path) {
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\","
                + "\"path\":\"" + path + "\",\"timestamp\":\"2026-01-05T10:15:30Z\"}";
    }

    private Map<String, String> sentBody() throws Exception {
        return LabJson.mapper().readValue(lab.lastRequest().body(), new TypeReference<Map<String, String>>() { });
    }

    // ---- PatientService.attemptRegister ----

    @Test
    // @regression
    @DisplayName("attemptRegister(name) sends the name as JSON to POST /api/patients and returns the typed answer")
    void attemptRegisterPatientSendsTheName() throws Exception {
        lab.answer("POST", "/api/patients", 201, "{\"id\":\"p-1\",\"name\":\"Ada-7c1e\"}");

        LabResponse<Patient> response = client.patients().attemptRegister("Ada-7c1e");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains(new Patient("p-1", "Ada-7c1e"));
        assertThat(lab.lastRequest().method()).isEqualTo("POST");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/patients");
        assertThat(sentBody()).isEqualTo(Map.of("name", "Ada-7c1e"));
    }

    @Test
    // @regression
    @DisplayName("attemptRegister(null) sends an empty JSON object, with no name field at all")
    void attemptRegisterPatientWithNullNameOmitsTheField() {
        lab.answer("POST", "/api/patients", 400,
                errorBody(400, "Bad Request", LabMessages.PATIENT_NAME_REQUIRED, "/api/patients"));

        LabResponse<Patient> response = client.patients().attemptRegister(null);

        assertThat(lab.lastRequest().body()).isEqualTo("{}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Patient name is required.");
    }

    @Test
    // @regression
    @DisplayName("attemptRegister sends a blank name as given, without trimming or dropping it")
    void attemptRegisterPatientSendsABlankNameAsGiven() throws Exception {
        lab.answer("POST", "/api/patients", 400,
                errorBody(400, "Bad Request", LabMessages.PATIENT_NAME_REQUIRED, "/api/patients"));

        client.patients().attemptRegister("   ");

        assertThat(sentBody()).isEqualTo(Map.of("name", "   "));
    }

    @Test
    // @regression
    @DisplayName("A refused patient registration is returned, not thrown, with the lab's message readable")
    void refusedPatientRegistrationIsReturnedNotThrown() {
        lab.answer("POST", "/api/patients", 400,
                errorBody(400, "Bad Request", LabMessages.PATIENT_NAME_REQUIRED, "/api/patients"));

        LabResponse<Patient> response = client.patients().attemptRegister("");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Patient name is required.");
        assertThat(response.describe()).isEqualTo("POST /api/patients -> 400 \"Patient name is required.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptRegister(name) sends the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptRegisterPatientSendsTheKeyOrNone() {
        lab.answer("POST", "/api/patients", 401, errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, "/api/patients"));

        client.patients().attemptRegister("Ada");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabResponse<Patient> anonymous = client.withoutApiKey().patients().attemptRegister("Ada");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.message()).isEqualTo("Unauthorized.");

        client.withApiKey("wrong").patients().attemptRegister("Ada");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer wrong");
    }

    // ---- OrderService.attemptPlace ----

    @Test
    // @regression
    @DisplayName("attemptPlace(patientId) sends the patient id as JSON to POST /api/orders and returns the typed answer")
    void attemptPlaceSendsThePatientId() throws Exception {
        lab.answer("POST", "/api/orders", 201, "{\"id\":\"o-1\",\"patientId\":\"" + PATIENT_ID + "\","
                + "\"createdAt\":\"2026-01-05T10:15:30Z\",\"samples\":[]}");

        LabResponse<Order> response = client.orders().attemptPlace(PATIENT_ID);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).hasValueSatisfying(order -> assertThat(order.id()).isEqualTo("o-1"));
        assertThat(lab.lastRequest().method()).isEqualTo("POST");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/orders");
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID));
    }

    @Test
    // @regression
    @DisplayName("attemptPlace(null) sends an empty JSON object, with no patientId field at all")
    void attemptPlaceWithNullPatientOmitsTheField() {
        lab.answer("POST", "/api/orders", 400,
                errorBody(400, "Bad Request", LabMessages.PATIENT_REQUIRED, "/api/orders"));

        LabResponse<Order> response = client.orders().attemptPlace(null);

        assertThat(lab.lastRequest().body()).isEqualTo("{}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Patient is required.");
    }

    @Test
    // @regression
    @DisplayName("A refused order (unknown patient) is returned, not thrown, with the lab's message readable")
    void refusedOrderIsReturnedNotThrown() {
        lab.answer("POST", "/api/orders", 404,
                errorBody(404, "Not Found", LabMessages.PATIENT_NOT_FOUND, "/api/orders"));

        LabResponse<Order> response = client.orders().attemptPlace("no-such-patient");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Patient not found.");
        assertThat(response.describe()).isEqualTo("POST /api/orders -> 404 \"Patient not found.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptPlace sends the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptPlaceSendsTheKeyOrNone() {
        lab.answer("POST", "/api/orders", 401, errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, "/api/orders"));

        client.orders().attemptPlace(PATIENT_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabResponse<Order> anonymous = client.withoutApiKey().orders().attemptPlace(PATIENT_ID);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    // ---- SampleService.attemptRegister ----

    @Test
    // @regression
    @DisplayName("attemptRegister sends a raw sample type verbatim, so a type the lab does not know reaches the lab")
    void attemptRegisterSampleSendsTheRawTypeVerbatim() throws Exception {
        lab.answer("POST", "/api/samples", 400,
                errorBody(400, "Bad Request", LabMessages.INVALID_SAMPLE_TYPE, "/api/samples"));

        LabResponse<Sample> response = client.samples().attemptRegister(PATIENT_ID, "PLASMA");

        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "PLASMA"));
        assertThat(lab.lastRequest().body()).doesNotContain("orderId");
        assertThat(lab.lastRequest().method()).isEqualTo("POST");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Invalid sample type.");
    }

    @Test
    // @regression
    @DisplayName("attemptRegister with an order id sends patientId, sampleType and orderId")
    void attemptRegisterSampleWithAnOrderSendsAllThree() throws Exception {
        lab.answer("POST", "/api/samples", 409,
                errorBody(409, "Conflict", LabMessages.DUPLICATE_SAMPLE_TYPE, "/api/samples"));

        LabResponse<Sample> response = client.samples().attemptRegister(PATIENT_ID, "BLOOD", ORDER_ID);

        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "BLOOD", "orderId", ORDER_ID));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("A sample of this type already exists for this order.");
    }

    @Test
    // @regression
    @DisplayName("attemptRegister leaves out every null argument: no orderId, no patientId, no sampleType")
    void attemptRegisterSampleOmitsNullArguments() throws Exception {
        lab.answer("POST", "/api/samples", 400,
                errorBody(400, "Bad Request", LabMessages.SAMPLE_TYPE_REQUIRED, "/api/samples"));

        client.samples().attemptRegister(PATIENT_ID, "BLOOD", null);
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "sampleType", "BLOOD"));

        client.samples().attemptRegister(PATIENT_ID, null, ORDER_ID);
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID, "orderId", ORDER_ID));

        client.samples().attemptRegister(null, "BLOOD", ORDER_ID);
        assertThat(sentBody()).isEqualTo(Map.of("sampleType", "BLOOD", "orderId", ORDER_ID));

        client.samples().attemptRegister(null, null);
        assertThat(lab.lastRequest().body()).isEqualTo("{}");

        client.samples().attemptRegister(PATIENT_ID, null);
        assertThat(sentBody()).isEqualTo(Map.of("patientId", PATIENT_ID));
        assertThat(lab.lastRequest().body()).doesNotContain("null");
    }

    @Test
    // @regression
    @DisplayName("A refused sample registration (400, 404, 409) is returned, not thrown, with the lab's message readable")
    void refusedSampleRegistrationIsReturnedNotThrown() {
        lab.answer("POST", "/api/samples", 404,
                errorBody(404, "Not Found", LabMessages.PATIENT_NOT_FOUND, "/api/samples"));
        LabResponse<Sample> notFound = client.samples().attemptRegister("no-such-patient", "BLOOD");
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(notFound.message()).isEqualTo("Patient not found.");
        assertThat(notFound.body()).isEmpty();

        lab.answer("POST", "/api/samples", 409,
                errorBody(409, "Conflict", LabMessages.ORDER_PATIENT_MISMATCH, "/api/samples"));
        LabResponse<Sample> conflict = client.samples().attemptRegister(PATIENT_ID, "BLOOD", ORDER_ID);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.describe())
                .isEqualTo("POST /api/samples -> 409 \"Order does not belong to this patient.\"");

        lab.answer("POST", "/api/samples", 400,
                errorBody(400, "Bad Request", LabMessages.PATIENT_REQUIRED, "/api/samples"));
        LabResponse<Sample> invalid = client.samples().attemptRegister(null, "BLOOD");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.message()).isEqualTo("Patient is required.");
    }

    @Test
    // @regression
    @DisplayName("A refusal that is not the lab's error body is returned too, with an empty message")
    void refusalWithoutALabBodyIsReturnedNotThrown() {
        lab.answer("POST", "/api/samples", 500, "<html>oops</html>");

        LabResponse<Sample> response = client.samples().attemptRegister(PATIENT_ID, "BLOOD");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.message()).isEmpty();
        assertThat(response.describe()).isEqualTo("POST /api/samples -> 500");
    }

    @Test
    // @regression
    @DisplayName("An accepted attempt returns the typed sample, so the caller can assert an acceptance too")
    void acceptedAttemptReturnsTheTypedSample() {
        lab.answer("POST", "/api/samples", 201, "{\"id\":\"s-1\",\"patientId\":\"" + PATIENT_ID
                + "\",\"orderId\":\"" + ORDER_ID + "\",\"sampleType\":\"SWAB\",\"status\":\"ACCESSIONED\","
                + "\"createdAt\":\"2026-01-05T10:15:30Z\"}");

        LabResponse<Sample> response = client.samples().attemptRegister(PATIENT_ID, "SWAB", ORDER_ID);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).hasValueSatisfying(sample -> {
            assertThat(sample.id()).isEqualTo("s-1");
            assertThat(sample.sampleType()).isEqualTo(SampleType.SWAB);
            assertThat(sample.status()).isEqualTo(WorkflowStage.ACCESSIONED);
        });
    }

    @Test
    // @regression
    @DisplayName("Both attemptRegister forms send the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptRegisterSampleSendsTheKeyOrNone() {
        lab.answer("POST", "/api/samples", 401, errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, "/api/samples"));

        client.samples().attemptRegister(PATIENT_ID, "BLOOD");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        client.samples().attemptRegister(PATIENT_ID, "BLOOD", ORDER_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabClient anonymous = client.withoutApiKey();
        LabResponse<Sample> twoArgs = anonymous.samples().attemptRegister(PATIENT_ID, "BLOOD");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        LabResponse<Sample> threeArgs = anonymous.samples().attemptRegister(PATIENT_ID, "BLOOD", ORDER_ID);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(twoArgs.statusCode()).isEqualTo(401);
        assertThat(threeArgs.message()).isEqualTo("Unauthorized.");
    }
}
