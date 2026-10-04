package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.client.http.LabResponse;
import com.labflow.client.support.StubLab;
import com.labflow.common.LabMessages;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.config.LabEnvironment;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the request-level {@code attempt…} twins, per functional-spec.md §2.6: a check
 * that expects the lab to refuse a request it cannot read ({@code attemptRegisterRaw} on
 * samples and on patients) or an action it does not support ({@code attemptDelete} on a
 * sample) gets the lab's answer back, never an exception. They run against a {@link StubLab},
 * so what was actually sent on the wire can be inspected.
 *
 * @spec: 003-test-automation-foundation
 */
class AttemptRequestLevelServicesTest {

    private static final String KEY = "s3cret-request-level-key-5518";
    private static final String SAMPLE_ID = "SMP-1";
    private static final String SAMPLE_PATH = "/api/samples/" + SAMPLE_ID;

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

    private void lab400(String path) {
        lab.answer("POST", path, 400, errorBody(400, "Bad Request", LabMessages.MALFORMED_REQUEST_BODY, path));
    }

    private void lab405() {
        lab.answer("DELETE", SAMPLE_PATH, 405,
                errorBody(405, "Method Not Allowed", LabMessages.METHOD_NOT_ALLOWED, SAMPLE_PATH),
                Map.of("Allow", "GET, HEAD, OPTIONS"));
    }

    // ---- SampleService.attemptRegisterRaw ----

    @ParameterizedTest(name = "[{index}] the text ''{0}''")
    // @regression
    @ValueSource(strings = {"{\"patientId\": ", "not json at all", "", "{\"patientId\":\"p-1\",}", "[1, 2"})
    @DisplayName("attemptRegisterRaw sends the given text to POST /api/samples byte for byte, without re-encoding it")
    void attemptRegisterRawSendsTheTextAsGiven(String rawBody) {
        lab400("/api/samples");

        client.samples().attemptRegisterRaw(rawBody);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("POST");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/samples");
        assertThat(lab.lastRequest().body()).isEqualTo(rawBody);
    }

    @Test
    // @regression
    @DisplayName("attemptRegisterRaw sends the body as JSON, so the lab reads the broken text as a JSON body")
    void attemptRegisterRawDeclaresJson() {
        lab400("/api/samples");

        client.samples().attemptRegisterRaw("not json at all");

        assertThat(lab.lastRequest().header("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
        assertThat(lab.lastRequest().header("Accept")).hasValueSatisfying(
                accept -> assertThat(accept).startsWith("application/json"));
    }

    @Test
    // @regression
    @DisplayName("attemptRegisterRaw sends non-ASCII text as UTF-8, unchanged")
    void attemptRegisterRawKeepsNonAsciiText() {
        lab400("/api/samples");

        client.samples().attemptRegisterRaw("{\"patientId\": \"Zoë-Łukasz");

        assertThat(lab.lastRequest().body()).isEqualTo("{\"patientId\": \"Zoë-Łukasz");
    }

    @Test
    // @regression
    @DisplayName("A sample registration with unreadable content is returned, not thrown, with the lab's message and error readable")
    void unreadableSampleRegistrationIsReturnedNotThrown() {
        lab400("/api/samples");

        LabResponse<Sample> response = client.samples().attemptRegisterRaw("{\"patientId\": ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Malformed request body.");
        assertThat(response.error()).hasValueSatisfying(error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error.error()).isEqualTo("Bad Request");
            assertThat(error.message()).isEqualTo("Malformed request body.");
            assertThat(error.path()).isEqualTo("/api/samples");
        });
        assertThat(response.describe()).isEqualTo("POST /api/samples -> 400 \"Malformed request body.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptRegisterRaw sends the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptRegisterRawOnSamplesSendsTheKeyOrNone() {
        lab.answer("POST", "/api/samples", 401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, "/api/samples"));

        client.samples().attemptRegisterRaw("not json at all");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabResponse<Sample> anonymous = client.withoutApiKey().samples().attemptRegisterRaw("not json at all");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(lab.lastRequest().body()).isEqualTo("not json at all");
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.message()).isEqualTo("Unauthorized.");
    }

    @Test
    // @regression
    @DisplayName("A raw body that looks like an access key is never repeated by describe() or the lab's message")
    void rawSampleBodyIsNeverEchoedInTheDescription() {
        String leaky = "{\"patientId\": \"Ada Lovelace\", \"apiKey\": \"s3cret-leaked-key-0042";
        lab400("/api/samples");

        LabResponse<Sample> response = client.samples().attemptRegisterRaw(leaky);

        assertThat(response.describe())
                .isEqualTo("POST /api/samples -> 400 \"Malformed request body.\"")
                .doesNotContain("s3cret").doesNotContain("Ada").doesNotContain("Lovelace")
                .doesNotContain(KEY).doesNotContain("Bearer");
        assertThat(response.message()).doesNotContain("s3cret").doesNotContain("Ada");
    }

    @Test
    // @regression
    @DisplayName("Even when the lab answers without its error body, describe() names only the request and the status")
    void rawSampleBodyIsNeverEchoedWhenTheLabAnswersWithSomethingElse() {
        lab.answer("POST", "/api/samples", 500, "<html>oops</html>");

        LabResponse<Sample> response = client.samples().attemptRegisterRaw("s3cret-leaked-key-0042");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.describe()).isEqualTo("POST /api/samples -> 500");
    }

    // ---- PatientService.attemptRegisterRaw ----

    @ParameterizedTest(name = "[{index}] the text ''{0}''")
    // @regression
    @ValueSource(strings = {"{\"name\": ", "not json at all", "", "{\"name\":\"Ada\",}"})
    @DisplayName("attemptRegisterRaw on patients sends the given text to POST /api/patients byte for byte")
    void attemptRegisterRawOnPatientsSendsTheTextAsGiven(String rawBody) {
        lab400("/api/patients");

        client.patients().attemptRegisterRaw(rawBody);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("POST");
        assertThat(lab.lastRequest().path()).isEqualTo("/api/patients");
        assertThat(lab.lastRequest().body()).isEqualTo(rawBody);
        assertThat(lab.lastRequest().header("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
    }

    @Test
    // @regression
    @DisplayName("A patient registration with unreadable content is returned, not thrown, with the lab's message and error readable")
    void unreadablePatientRegistrationIsReturnedNotThrown() {
        lab400("/api/patients");

        LabResponse<Patient> response = client.patients().attemptRegisterRaw("not json at all");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Malformed request body.");
        assertThat(response.error()).hasValueSatisfying(error -> {
            assertThat(error.error()).isEqualTo("Bad Request");
            assertThat(error.path()).isEqualTo("/api/patients");
        });
        assertThat(response.describe()).isEqualTo("POST /api/patients -> 400 \"Malformed request body.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptRegisterRaw on patients sends the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptRegisterRawOnPatientsSendsTheKeyOrNone() {
        lab.answer("POST", "/api/patients", 401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, "/api/patients"));

        client.patients().attemptRegisterRaw("not json at all");
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabResponse<Patient> anonymous = client.withoutApiKey().patients().attemptRegisterRaw("not json at all");
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    @Test
    // @regression
    @DisplayName("A raw patient body with a name and a key-looking string is never repeated by describe()")
    void rawPatientBodyIsNeverEchoedInTheDescription() {
        String leaky = "{\"name\": \"Grace Hopper\", \"token\": \"s3cret-leaked-key-0042";
        lab400("/api/patients");

        LabResponse<Patient> response = client.patients().attemptRegisterRaw(leaky);

        assertThat(response.describe())
                .isEqualTo("POST /api/patients -> 400 \"Malformed request body.\"")
                .doesNotContain("s3cret").doesNotContain("Grace").doesNotContain("Hopper")
                .doesNotContain(KEY).doesNotContain("Bearer");
    }

    // ---- SampleService.attemptDelete ----

    @Test
    // @regression
    @DisplayName("attemptDelete sends DELETE /api/samples/{id} with no body")
    void attemptDeleteSendsADeleteToTheSamplePath() {
        lab405();

        client.samples().attemptDelete(SAMPLE_ID);

        assertThat(lab.requests()).hasSize(1);
        assertThat(lab.lastRequest().method()).isEqualTo("DELETE");
        assertThat(lab.lastRequest().path()).isEqualTo(SAMPLE_PATH);
        assertThat(lab.lastRequest().body()).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("A refused delete (405) is returned, not thrown, with the lab's message, error and Allow header readable")
    void refusedDeleteIsReturnedNotThrown() {
        lab405();

        LabResponse<Void> response = client.samples().attemptDelete(SAMPLE_ID);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.body()).isEmpty();
        assertThat(response.message()).isEqualTo("Method not allowed.");
        assertThat(response.error()).hasValueSatisfying(error -> {
            assertThat(error.status()).isEqualTo(405);
            assertThat(error.error()).isEqualTo("Method Not Allowed");
            assertThat(error.path()).isEqualTo(SAMPLE_PATH);
        });
        assertThat(response.header("Allow")).contains("GET, HEAD, OPTIONS");
        assertThat(response.header("allow")).contains("GET, HEAD, OPTIONS");
        assertThat(response.describe()).isEqualTo("DELETE /api/samples/SMP-1 -> 405 \"Method not allowed.\"");
    }

    @Test
    // @regression
    @DisplayName("attemptDelete sends the key as 'Authorization: Bearer <key>', and none after withoutApiKey()")
    void attemptDeleteSendsTheKeyOrNone() {
        lab.answer("DELETE", SAMPLE_PATH, 401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED, SAMPLE_PATH));

        client.samples().attemptDelete(SAMPLE_ID);
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + KEY);

        LabResponse<Void> anonymous = client.withoutApiKey().samples().attemptDelete(SAMPLE_ID);
        assertThat(lab.lastRequest().headers()).doesNotContainKey("Authorization");
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.message()).isEqualTo("Unauthorized.");
    }

    @Test
    // @regression
    @DisplayName("describe() of a refused delete names the request and status only: no key, headers or Bearer")
    void deleteDescriptionCarriesNoKeyOrHeaders() {
        lab405();

        String description = client.samples().attemptDelete(SAMPLE_ID).describe();

        assertThat(description).doesNotContain(KEY).doesNotContain("Bearer").doesNotContain("Allow")
                .doesNotContain("GET, HEAD");
    }

    @Test
    // @regression
    @DisplayName("A delete the lab would accept (a stub answering 204 with no content) is returned with an empty body, not thrown")
    void acceptedDeleteHasNoBodyToRead() {
        lab.answer("DELETE", SAMPLE_PATH, 204, "");

        LabResponse<Void> response = client.samples().attemptDelete(SAMPLE_ID);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.error()).isEmpty();
        assertThat(response.describe()).isEqualTo("DELETE /api/samples/SMP-1 -> 204");
    }

    @Test
    // @regression
    @DisplayName("A refused delete whose body is not the lab's error body still returns the status and the Allow header")
    void refusedDeleteWithoutALabBodyIsStillReturned() {
        lab.answer("DELETE", SAMPLE_PATH, 405, "<html>no</html>", Map.of("Allow", "GET"));

        LabResponse<Void> response = client.samples().attemptDelete(SAMPLE_ID);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.error()).isEmpty();
        assertThat(response.message()).isEmpty();
        assertThat(response.header("Allow")).contains("GET");
        assertThat(response.describe()).isEqualTo("DELETE /api/samples/SMP-1 -> 405");
    }
}
