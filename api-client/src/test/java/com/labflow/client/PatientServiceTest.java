package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.client.api.PatientApi;
import com.labflow.client.http.LabJson;
import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.http.LabResponse;
import com.labflow.client.http.UnexpectedResponseError;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.config.LabEnvironment;
import com.labflow.common.model.Patient;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the patient client layers ({@link LabClient}, {@code PatientService},
 * {@code PatientApi}, {@link LabResponse}), per functional-spec.md §2.2 (the lab layer
 * does the calling and fails clearly) and §2.5 (register and look up a patient). They run
 * against a {@link StubLab}, so no real lab is needed and what was actually sent on the
 * wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class PatientServiceTest {

    private static final String KEY = "s3cret-service-key-4410";
    private static final String NAME = "Ada-Lovelace-3f9a";
    private static final String CONFLICT_BODY = "{\"status\":409,\"error\":\"Conflict\","
            + "\"message\":\"Order does not belong to this patient.\",\"path\":\"/api/patients\","
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

    private PatientApi api(ApiKey key) {
        return new PatientApi(() -> LabRequestSpecs.create(lab.baseUrl(), key));
    }

    // ---- what the service returns and sends ----

    @Test
    // @regression
    @DisplayName("Registering a patient returns the lab's patient and sends the name as JSON to POST /api/patients")
    void registerReturnsThePatientTheLabAssigned() throws Exception {
        lab.answer("POST", "/api/patients", 201, "{\"id\":\"p-1\",\"name\":\"" + NAME + "\"}");

        Patient patient = client.patients().register(NAME);

        assertThat(patient).isEqualTo(new Patient("p-1", NAME));
        StubLab.ReceivedRequest sent = lab.lastRequest();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/api/patients");
        assertThat(sent.header("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(LabJson.mapper().readValue(sent.body(), new TypeReference<Map<String, String>>() { }))
                .isEqualTo(Map.of("name", NAME));
    }

    @Test
    // @regression
    @DisplayName("Looking a patient up returns the lab's patient from GET /api/patients/{id}")
    void findReturnsThePatient() {
        lab.answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"" + NAME + "\"}");

        Patient patient = client.patients().find("p-1");

        assertThat(patient).isEqualTo(new Patient("p-1", NAME));
        assertThat(lab.lastRequest().method()).isEqualTo("GET");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/patients/p-1");
    }

    // ---- the key on the wire ----

    @Test
    // @regression
    @DisplayName("The configured key goes out as 'Authorization: Bearer <key>'")
    void configuredKeyIsSentAsBearer() {
        lab.answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"n\"}");

        client.patients().find("p-1");

        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
    }

    @Test
    // @regression
    @DisplayName("withoutApiKey() sends no Authorization header at all")
    void withoutApiKeySendsNoAuthorizationHeader() {
        lab.answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"n\"}");

        client.withoutApiKey().patients().find("p-1");

        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
    }

    @Test
    // @regression
    @DisplayName("withApiKey(\"wrong\") sends that key, and the original client is unchanged")
    void withApiKeySendsTheGivenKeyAndLeavesTheOriginalAlone() {
        lab.answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"n\"}");
        LabClient wrong = client.withApiKey("wrong");

        wrong.patients().find("p-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer wrong");

        client.patients().find("p-1");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);
        assertThat(wrong).isNotSameAs(client);
        assertThat(client.withoutApiKey()).isNotSameAs(client);
    }

    @Test
    // @regression
    @DisplayName("A client asked to use a blank key is refused rather than silently sending none")
    void blankKeyIsRefused() {
        assertThatThrownBy(() -> client.withApiKey(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- an unexpected answer fails clearly ----

    @Test
    // @regression
    @DisplayName("An unexpected status makes register throw an error that names the request, both statuses and the lab's message")
    void unexpectedStatusThrowsWithTheLabsMessage() {
        lab.answer("POST", "/api/patients", 409, CONFLICT_BODY, Map.of("X-Debug", "header-value-9911"));

        assertThatThrownBy(() -> client.patients().register(NAME))
                .isInstanceOf(UnexpectedResponseError.class)
                .isInstanceOf(AssertionError.class)
                .hasMessage("POST /api/patients expected 201 but the lab answered 409 "
                        + "\"Order does not belong to this patient.\"");
    }

    @Test
    // @regression
    @DisplayName("The failure message contains no header values, no key and no patient name")
    void failureMessageLeaksNothing() {
        lab.answer("POST", "/api/patients", 409, CONFLICT_BODY, Map.of("X-Debug", "header-value-9911"));

        assertThatThrownBy(() -> client.patients().register(NAME))
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("header-value-9911")
                .hasMessageNotContaining(NAME);
    }

    @Test
    // @regression
    @DisplayName("A failed lookup names the request, both statuses and the lab's message")
    void failedLookupThrowsWithTheLabsMessage() {
        lab.answer("GET", "/api/patients/nope", 404,
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Patient not found.\","
                        + "\"path\":\"/api/patients/nope\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        assertThatThrownBy(() -> client.patients().find("nope"))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("GET /api/patients/nope expected 200 but the lab answered 404 \"Patient not found.\"");
    }

    @Test
    // @regression
    @DisplayName("A refusal without a readable lab message is reported by status alone")
    void refusalWithoutLabMessageIsReportedByStatusOnly() {
        lab.answer("POST", "/api/patients", 502, "<html>Bad gateway</html>");

        assertThatThrownBy(() -> client.patients().register(NAME))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("POST /api/patients expected 201 but the lab answered 502");
    }

    @Test
    // @regression
    @DisplayName("A right status with an unreadable body is an error too, not a silent null")
    void rightStatusButUnreadableBodyIsAnError() {
        lab.answer("POST", "/api/patients", 201, "this is not json");

        assertThatThrownBy(() -> client.patients().register(NAME))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessageContaining("POST /api/patients expected 201 and the lab answered 201")
                .hasMessageContaining("Patient");
    }

    @Test
    // @regression
    @DisplayName("A 2xx answer with the wrong 2xx status is not accepted as the expected one")
    void otherSuccessStatusIsNotTheExpectedOne() {
        lab.answer("POST", "/api/patients", 200, "{\"id\":\"p-1\",\"name\":\"n\"}");

        assertThatThrownBy(() -> client.patients().register(NAME))
                .isInstanceOf(UnexpectedResponseError.class)
                .hasMessage("POST /api/patients expected 201 but the lab answered 200");
    }

    // ---- the response wrapper ----

    @Test
    // @regression
    @DisplayName("describe() of a success is 'POST /api/patients -> 201' and the body and headers are readable")
    void describeOfASuccess() {
        lab.answer("POST", "/api/patients", 201, "{\"id\":\"p-1\",\"name\":\"" + NAME + "\"}",
                Map.of("Location", "/api/patients/p-1"));

        LabResponse<Patient> response = api(new ApiKey(KEY)).register(Map.of("name", NAME));

        assertThat(response.describe()).isEqualTo("POST /api/patients -> 201");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains(new Patient("p-1", NAME));
        assertThat(response.error()).isEmpty();
        assertThat(response.message()).isEmpty();
        assertThat(response.header("location")).contains("/api/patients/p-1");
        assertThat(response.header("LOCATION")).contains("/api/patients/p-1");
        assertThat(response.header("X-Not-Sent")).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("describe() of a refusal is 'POST /api/patients -> 409 \"message\"' on one line, with no headers, key or name")
    void describeOfARefusalIsOneLineWithoutSecretsOrNames() {
        lab.answer("POST", "/api/patients", 409, CONFLICT_BODY, Map.of("X-Debug", "header-value-9911"));

        LabResponse<Patient> response = api(new ApiKey(KEY)).register(Map.of("name", NAME));

        assertThat(response.describe())
                .isEqualTo("POST /api/patients -> 409 \"Order does not belong to this patient.\"")
                .doesNotContain("\n")
                .doesNotContain("header-value-9911")
                .doesNotContain(KEY)
                .doesNotContain(NAME);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Order does not belong to this patient.");
        assertThat(response.error()).hasValueSatisfying(error -> {
            assertThat(error.status()).isEqualTo(409);
            assertThat(error.error()).isEqualTo("Conflict");
        });
    }

    @Test
    // @regression
    @DisplayName("A refusal that is not the lab's error body has no error and an empty message")
    void refusalThatIsNotAnErrorBodyHasNoError() {
        lab.answer("POST", "/api/patients", 500, "<html>oops</html>");

        LabResponse<Patient> response = api(new ApiKey(KEY)).register(Map.of("name", NAME));

        assertThat(response.error()).isEmpty();
        assertThat(response.message()).isEmpty();
        assertThat(response.body()).isEmpty();
        assertThat(response.describe()).isEqualTo("POST /api/patients -> 500");
    }

    @Test
    // @regression
    @DisplayName("The Api layer never asserts: a refusal comes back as an ordinary response")
    void apiLayerReturnsRefusalsInsteadOfThrowing() {
        lab.answer("GET", "/api/patients/nope", 404,
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Patient not found.\","
                        + "\"path\":\"/api/patients/nope\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        LabResponse<Patient> response = api(new ApiKey(KEY)).find("nope");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.describe()).isEqualTo("GET /api/patients/nope -> 404 \"Patient not found.\"");
    }

    @Test
    // @regression
    @DisplayName("A raw String body is sent to the lab exactly as given, even when it is not valid JSON")
    void rawStringBodyIsSentVerbatim() {
        lab.answer("POST", "/api/patients", 400,
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body.\","
                        + "\"path\":\"/api/patients\",\"timestamp\":\"2026-01-05T10:15:30Z\"}");

        LabResponse<Patient> response = api(new ApiKey(KEY)).register("{\"name\": ");

        assertThat(lab.lastRequest().body()).isEqualTo("{\"name\": ");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Malformed request body.");
    }

    // ---- no global state ----

    @Test
    // @regression
    @DisplayName("Using the client leaves RestAssured's static defaults untouched")
    void restAssuredStaticDefaultsAreUntouched() {
        lab.answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"n\"}");

        client.patients().find("p-1");
        client.withoutApiKey().patients().find("p-1");

        assertThat(RestAssured.baseURI).isEqualTo(RestAssured.DEFAULT_URI);
        assertThat(RestAssured.port).isEqualTo(RestAssured.UNDEFINED_PORT);
        assertThat(RestAssured.basePath).isEqualTo(RestAssured.DEFAULT_PATH);
        assertThat(RestAssured.requestSpecification).isNull();
        assertThat(RestAssured.responseSpecification).isNull();
    }
}
