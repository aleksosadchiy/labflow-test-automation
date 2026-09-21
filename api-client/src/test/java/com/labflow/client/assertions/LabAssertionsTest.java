package com.labflow.client.assertions;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.client.LabClient;
import com.labflow.client.http.LabResponse;
import com.labflow.client.http.Rejection;
import com.labflow.client.support.StubLab;
import com.labflow.common.LabMessages;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Sample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the custom assertions over a lab answer, per functional-spec.md §2.6 (a refusal
 * is checked by kind and exact message, plus headers) and §2.7 (a failure names the request
 * and the lab's own message, and never leaks a key or unrelated headers). They run against a
 * {@link StubLab}: the responses under test are real {@link LabResponse}s produced by the
 * real client, so the status, message and headers come through the same path as in a check.
 *
 * @spec: 003-test-automation-foundation
 */
class LabAssertionsTest {

    private static final String KEY = "s3cret-assert-key-5521";
    private static final String PATIENT_ID = "p-4471";
    private static final String ORDER_ID = "o-1";
    private static final String DEBUG_HEADER_VALUE = "debug-header-value-8815";
    private static final String COOKIE_HEADER_VALUE = "session=cookie-value-2291";
    private static final String AUTH_HEADER_VALUE = "Bearer leaked-auth-value-4407";
    private static final String DUPLICATE = LabMessages.DUPLICATE_SAMPLE_TYPE;

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

    private static String errorBody(int status, String error, String message) {
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\","
                + "\"path\":\"/api/samples\",\"timestamp\":\"2026-01-05T10:15:30Z\"}";
    }

    /** Has the lab answer the next sample registration, then sends one and returns what came back. */
    private LabResponse<Sample> registrationAnsweredWith(int status, String body, Map<String, String> headers) {
        lab.answer("POST", "/api/samples", status, body, headers);
        return client.samples().attemptRegister(PATIENT_ID, "BLOOD", ORDER_ID);
    }

    private LabResponse<Sample> refusal(int status, String error, String message) {
        return registrationAnsweredWith(status, errorBody(status, error, message), Map.of());
    }

    private static final String CREATED_BODY = "{\"id\":\"s-1\",\"patientId\":\"" + PATIENT_ID
            + "\",\"orderId\":\"" + ORDER_ID + "\",\"sampleType\":\"BLOOD\",\"status\":\"ACCESSIONED\","
            + "\"createdAt\":\"2026-01-05T10:15:30Z\"}";

    // ---- isRejectedAs: passes ----

    static Stream<Arguments> everyKindOfRejection() {
        return Stream.of(
                Arguments.of(Rejection.INVALID_INPUT, 400, "Bad Request", LabMessages.PATIENT_NAME_REQUIRED),
                Arguments.of(Rejection.UNAUTHORIZED, 401, "Unauthorized", LabMessages.UNAUTHORIZED),
                Arguments.of(Rejection.NOT_FOUND, 404, "Not Found", LabMessages.SAMPLE_NOT_FOUND),
                Arguments.of(Rejection.METHOD_NOT_ALLOWED, 405, "Method Not Allowed", LabMessages.METHOD_NOT_ALLOWED),
                Arguments.of(Rejection.CONFLICT, 409, "Conflict", LabMessages.DUPLICATE_SAMPLE_TYPE));
    }

    @ParameterizedTest(name = "{0} passes for a {1} answer")
    // @regression
    @MethodSource("everyKindOfRejection")
    @DisplayName("A refusal of the expected kind with the lab's exact message passes, for every kind of refusal")
    void correctRejectionPasses(Rejection kind, int status, String error, String message) {
        LabResponse<Sample> response = refusal(status, error, message);

        assertThatCode(() -> LabAssertions.assertThat(response).isRejectedAs(kind, message))
                .doesNotThrowAnyException();
    }

    // ---- isRejectedAs: fails ----

    @Test
    // @regression
    @DisplayName("A refusal of another kind fails, naming the request, the expected kind and message, and what the lab answered")
    void wrongKindFailsWithTheFullPicture() {
        LabResponse<Sample> response = refusal(404, "Not Found", LabMessages.SAMPLE_NOT_FOUND);

        assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 404 \"Sample not found.\"] expected rejection CONFLICT "
                        + "\"A sample of this type already exists for this order.\" "
                        + "but the lab answered 404 \"Sample not found.\"");
    }

    @Test
    // @regression
    @DisplayName("The right kind with different wording fails, naming both the expected and the actual message")
    void rightKindWrongMessageFailsNamingBothMessages() {
        LabResponse<Sample> response = refusal(409, "Conflict", LabMessages.ORDER_PATIENT_MISMATCH);

        assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 409 \"Order does not belong to this patient.\"] "
                        + "expected rejection CONFLICT \"A sample of this type already exists for this order.\" "
                        + "but the lab said \"Order does not belong to this patient.\"");
    }

    @Test
    // @regression
    @DisplayName("The message must match exactly: a different capitalisation or a missing full stop fails")
    void messageMustMatchExactly() {
        LabResponse<Sample> response = refusal(409, "Conflict", DUPLICATE);

        assertThatThrownBy(() -> LabAssertions.assertThat(response)
                .isRejectedAs(Rejection.CONFLICT, DUPLICATE.toLowerCase()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but the lab said \"" + DUPLICATE + "\"");
        assertThatThrownBy(() -> LabAssertions.assertThat(response)
                .isRejectedAs(Rejection.CONFLICT, DUPLICATE.substring(0, DUPLICATE.length() - 1)))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LabAssertions.assertThat(response)
                .isRejectedAs(Rejection.CONFLICT, DUPLICATE + " "))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    // @regression
    @DisplayName("The right kind but no readable lab message fails, saying the lab gave no message")
    void rightKindWithoutALabMessageFails() {
        LabResponse<Sample> response = registrationAnsweredWith(409, "<html>conflict</html>", Map.of());

        assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 409] expected rejection CONFLICT "
                        + "\"A sample of this type already exists for this order.\" but the lab gave no message");
    }

    @Test
    // @regression
    @DisplayName("A success answer is not a rejection: a 201 fails and says the lab answered 201")
    void successAnswerFails() {
        LabResponse<Sample> response = registrationAnsweredWith(201, CREATED_BODY, Map.of());

        assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 201] expected rejection CONFLICT "
                        + "\"A sample of this type already exists for this order.\" but the lab answered 201");
    }

    @Test
    // @regression
    @DisplayName("A refusal with a status outside the five kinds (a 500) fails for every kind")
    void unlistedStatusFailsForEveryKind() {
        LabResponse<Sample> response = registrationAnsweredWith(500, "<html>oops</html>", Map.of());

        for (Rejection kind : Rejection.values()) {
            assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(kind, DUPLICATE))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("[POST /api/samples -> 500] expected rejection " + kind
                            + " \"A sample of this type already exists for this order.\" but the lab answered 500");
        }
    }

    @Test
    // @regression
    @DisplayName("A '%' in the lab's message is reported as text, not read as a format instruction")
    void percentSignInTheLabsMessageIsReportedAsText() {
        LabResponse<Sample> response = refusal(409, "Conflict", "100% sure %s %d");

        assertThatThrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but the lab said \"100% sure %s %d\"");
    }

    @Test
    // @regression
    @DisplayName("Asserting on a missing response fails as an assertion, not with a null pointer")
    void missingResponseFailsAsAnAssertion() {
        assertThatThrownBy(() -> LabAssertions.assertThat(null).isRejectedAs(Rejection.CONFLICT, DUPLICATE))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LabAssertions.assertThat(null).hasNoLocationHeader())
                .isInstanceOf(AssertionError.class);
    }

    // ---- headers ----

    @Test
    // @regression
    @DisplayName("hasHeader passes on an exact value, whatever the capitalisation of the header name")
    void hasHeaderPassesCaseInsensitively() {
        LabResponse<Sample> response = registrationAnsweredWith(401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED), Map.of("WWW-Authenticate", "Bearer"));

        assertThatCode(() -> LabAssertions.assertThat(response)
                .hasHeader("WWW-Authenticate", "Bearer")
                .hasHeader("www-authenticate", "Bearer")
                .hasHeader("WWW-AUTHENTICATE", "Bearer")).doesNotThrowAnyException();
    }

    @Test
    // @regression
    @DisplayName("hasHeader fails on a different value, showing that header's actual value")
    void hasHeaderFailsOnAWrongValue() {
        LabResponse<Sample> response = registrationAnsweredWith(401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED), Map.of("WWW-Authenticate", "Bearer"));

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasHeader("WWW-Authenticate", "Basic"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 401 \"Unauthorized.\"] "
                        + "expected header \"WWW-Authenticate\" to be \"Basic\" but it was \"Bearer\"");
        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasHeader("WWW-Authenticate", "bearer"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    // @regression
    @DisplayName("hasHeader fails when the header is absent")
    void hasHeaderFailsWhenAbsent() {
        LabResponse<Sample> response = refusal(401, "Unauthorized", LabMessages.UNAUTHORIZED);

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasHeader("WWW-Authenticate", "Bearer"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 401 \"Unauthorized.\"] "
                        + "expected header \"WWW-Authenticate\" to be \"Bearer\" but the lab sent no such header");
    }

    @Test
    // @regression
    @DisplayName("hasHeaderContaining passes when the value contains the fragment, whatever the capitalisation of the name")
    void hasHeaderContainingPasses() {
        LabResponse<Sample> response = registrationAnsweredWith(405,
                errorBody(405, "Method Not Allowed", LabMessages.METHOD_NOT_ALLOWED), Map.of("Allow", "GET, HEAD, OPTIONS"));

        assertThatCode(() -> LabAssertions.assertThat(response)
                .hasHeaderContaining("Allow", "GET")
                .hasHeaderContaining("allow", "OPTIONS")
                .hasHeaderContaining("ALLOW", "GET, HEAD, OPTIONS")).doesNotThrowAnyException();
    }

    @Test
    // @regression
    @DisplayName("hasHeaderContaining fails when the fragment is missing, showing that header's actual value")
    void hasHeaderContainingFailsWhenTheFragmentIsMissing() {
        LabResponse<Sample> response = registrationAnsweredWith(405,
                errorBody(405, "Method Not Allowed", LabMessages.METHOD_NOT_ALLOWED), Map.of("Allow", "GET, HEAD, OPTIONS"));

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasHeaderContaining("Allow", "DELETE"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 405 \"Method not allowed.\"] "
                        + "expected header \"Allow\" to contain \"DELETE\" but it was \"GET, HEAD, OPTIONS\"");
    }

    @Test
    // @regression
    @DisplayName("hasHeaderContaining fails when the header is absent")
    void hasHeaderContainingFailsWhenAbsent() {
        LabResponse<Sample> response = refusal(405, "Method Not Allowed", LabMessages.METHOD_NOT_ALLOWED);

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasHeaderContaining("Allow", "GET"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 405 \"Method not allowed.\"] "
                        + "expected header \"Allow\" to contain \"GET\" but the lab sent no such header");
    }

    @Test
    // @regression
    @DisplayName("hasNoLocationHeader passes for a refusal without a Location header")
    void hasNoLocationHeaderPassesForARefusal() {
        LabResponse<Sample> response = refusal(409, "Conflict", DUPLICATE);

        assertThatCode(() -> LabAssertions.assertThat(response).hasNoLocationHeader()).doesNotThrowAnyException();
    }

    @Test
    // @regression
    @DisplayName("hasNoLocationHeader fails on a 201 that carries a Location header, showing the Location")
    void hasNoLocationHeaderFailsOnACreatedAnswer() {
        LabResponse<Sample> response = registrationAnsweredWith(201, CREATED_BODY,
                Map.of("Location", "/api/samples/s-1"));

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasNoLocationHeader())
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 201] expected no Location header but the lab sent \"/api/samples/s-1\"");
    }

    @Test
    // @regression
    @DisplayName("A Location header is found whatever the capitalisation the lab used")
    void locationHeaderIsFoundCaseInsensitively() {
        LabResponse<Sample> response = registrationAnsweredWith(201, CREATED_BODY,
                Map.of("LOCATION", "/api/samples/s-1"));

        assertThatThrownBy(() -> LabAssertions.assertThat(response).hasNoLocationHeader())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/api/samples/s-1");
    }

    @Test
    // @regression
    @DisplayName("The checks chain: a refusal can be checked for kind, message, header and no Location in one statement")
    void checksChain() {
        LabResponse<Sample> response = registrationAnsweredWith(401,
                errorBody(401, "Unauthorized", LabMessages.UNAUTHORIZED), Map.of("WWW-Authenticate", "Bearer"));

        assertThatCode(() -> LabAssertions.assertThat(response)
                .isRejectedAs(Rejection.UNAUTHORIZED, LabMessages.UNAUTHORIZED)
                .hasHeader("WWW-Authenticate", "Bearer")
                .hasHeaderContaining("WWW-Authenticate", "Bear")
                .hasNoLocationHeader()).doesNotThrowAnyException();
    }

    // ---- failure text never leaks ----

    @Test
    // @regression
    @DisplayName("Failure text never contains other headers' values, the API key, the request body or 'Bearer'")
    void failureTextLeaksNothing() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Debug", DEBUG_HEADER_VALUE);
        headers.put("Set-Cookie", COOKIE_HEADER_VALUE);
        headers.put("Authorization", AUTH_HEADER_VALUE);
        headers.put("Allow", "GET, HEAD");
        headers.put("Location", "/api/samples/s-1");
        LabResponse<Sample> response = registrationAnsweredWith(409, errorBody(409, "Conflict", LabMessages.ORDER_PATIENT_MISMATCH), headers);
        LabResponse<Sample> created = registrationAnsweredWith(201, CREATED_BODY, headers);

        List<Throwable> failures = List.of(
                thrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.NOT_FOUND, DUPLICATE)),
                thrownBy(() -> LabAssertions.assertThat(response).isRejectedAs(Rejection.CONFLICT, DUPLICATE)),
                thrownBy(() -> LabAssertions.assertThat(created).isRejectedAs(Rejection.CONFLICT, DUPLICATE)),
                thrownBy(() -> LabAssertions.assertThat(response).hasHeader("Allow", "POST")),
                thrownBy(() -> LabAssertions.assertThat(response).hasHeader("X-Absent", "1")),
                thrownBy(() -> LabAssertions.assertThat(response).hasHeaderContaining("Allow", "DELETE")),
                thrownBy(() -> LabAssertions.assertThat(response).hasHeaderContaining("X-Absent", "1")),
                thrownBy(() -> LabAssertions.assertThat(created).hasNoLocationHeader()));

        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("[POST /api/samples -> ")
                .hasMessageNotContaining(DEBUG_HEADER_VALUE)
                .hasMessageNotContaining(COOKIE_HEADER_VALUE)
                .hasMessageNotContaining("leaked-auth-value")
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining(PATIENT_ID)
                .hasMessageNotContaining(ORDER_ID)
                .hasMessageNotContaining("sampleType"));
    }

    @Test
    // @regression
    @DisplayName("Naming the Authorization header in a check reports only that it was or was not present, never its value")
    void authorizationValueIsNeverPrinted() {
        LabResponse<Sample> withAuthorization = registrationAnsweredWith(409,
                errorBody(409, "Conflict", DUPLICATE), Map.of("Authorization", AUTH_HEADER_VALUE));
        LabResponse<Sample> without = refusal(409, "Conflict", DUPLICATE);

        assertThatThrownBy(() -> LabAssertions.assertThat(withAuthorization)
                .hasHeader("Authorization", "Bearer guess-7788"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 409 \"A sample of this type already exists for this order.\"] "
                        + "expected header \"Authorization\" but its value differs (not shown)");
        assertThatThrownBy(() -> LabAssertions.assertThat(withAuthorization)
                .hasHeaderContaining("authorization", "guess-7788"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 409 \"A sample of this type already exists for this order.\"] "
                        + "expected header \"authorization\" but its value does not (not shown)");
        assertThatThrownBy(() -> LabAssertions.assertThat(without).hasHeader("Authorization", "Bearer guess-7788"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[POST /api/samples -> 409 \"A sample of this type already exists for this order.\"] "
                        + "expected header \"Authorization\" but the lab sent no such header");
        assertThatThrownBy(() -> LabAssertions.assertThat(without)
                .hasHeaderContaining("Authorization", "guess-7788"))
                .isInstanceOf(AssertionError.class)
                .hasMessageNotContaining("guess-7788");
    }

    private static Throwable thrownBy(Runnable check) {
        try {
            check.run();
        } catch (Throwable failure) {
            return failure;
        }
        throw new AssertionError("The assertion was expected to fail but passed");
    }
}
