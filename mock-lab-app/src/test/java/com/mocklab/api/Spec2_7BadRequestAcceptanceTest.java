package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of functional-spec.md section 2.7 (Handling Bad Requests): the part of
 * the first criterion the slices could not observe, "...and nothing is created". The lab has
 * three create routes; each is sent every kind of broken body, and none may store anything.
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>Unreadable content (broken or incomplete data): "Malformed request body.", nothing created
 *       or changed.</b> The rejection itself, per resource:
 *       {@code PatientApiAcceptanceTest#brokenJsonIsMalformedAndNeverEchoesThePayload},
 *       {@code #aJsonArrayInsteadOfAnObjectIsMalformed}, {@code #aMissingBodyIsMalformed},
 *       {@code OrderApiAcceptanceTest#brokenJsonIsMalformedAndNeverEchoesThePayload} (and its two siblings),
 *       {@code SampleApiAcceptanceTest#brokenJsonIsMalformedAndNeverEchoesThePayload} (and its two siblings),
 *       {@code ApiErrorMappingIntegrationTest#brokenJsonBodyIsMalformedAndNeverEchoesThePayload},
 *       {@code #wrongShapeBodyIsAlsoMalformed}. "Nothing changed" for a stage change:
 *       {@code SampleStatusApiAcceptanceTest#aBodyThatIsNotAStatusObjectIsMalformedAndTheSampleStaysPut},
 *       {@code #aRequestWithNoBodyAtAllIsMalformedAndTheSampleStaysPut}. "Nothing created" for the
 *       three create routes and every kind of broken body:
 *       {@link #aBrokenBodyIsRejectedAsMalformedAndNothingIsCreated}.</li>
 *   <li><b>Unsupported action: "Method not allowed.", nothing changed.</b>
 *       {@code PatientApiAcceptanceTest#deleteIsNotAllowedAndTheAllowHeaderListsGet},
 *       {@code OrderApiAcceptanceTest#deleteIsNotAllowedAndTheAllowHeaderListsGet},
 *       {@code SampleApiAcceptanceTest#deleteIsNotAllowedAndTheAllowHeaderListsGet} (sample still readable),
 *       {@code SampleStatusApiAcceptanceTest#anyVerbOtherThanPutOnTheStatusPathIsNotAllowedAndTheAllowHeaderListsPut},
 *       {@code SampleReportApiAcceptanceTest#anyVerbOtherThanGetIsNotAllowedAndTheAllowHeaderListsGet},
 *       {@code ApiErrorMappingIntegrationTest#wrongVerbIsMethodNotAllowedWithAnAllowHeader}.</li>
 *   <li><b>Something that does not exist at all: "Resource not found."</b>
 *       {@code ApiErrorMappingIntegrationTest#unknownRouteWithKeyIsNotFoundInTheStandardBody},
 *       {@code #anUnknownRouteIsNotFoundWhateverTheVerbOrTrailingSlash}.</li>
 *   <li><b>Any rejection carries a readable message.</b> Every rejection assertion in the API
 *       tests goes through {@code AbstractApiAcceptanceTest#expectError}, which requires
 *       status, error, message, path and timestamp; the exact texts are pinned by
 *       {@code LabMessagesTest}; the three framework-level additions and the unexpected-failure
 *       case by {@code ApiErrorMappingIntegrationTest#unsupportedMediaTypeIs415},
 *       {@code #notAcceptableIs406AndStillJson}, {@code #unexpectedFailureIs500WithoutExceptionTextOrStackTrace},
 *       {@code #eachFailureKindMapsToItsStatusAndCarriesTheExceptionMessage}.</li>
 * </ol>
 *
 * <h2>Two malformed bodies that the parser once let through</h2>
 * Jackson is lenient by default in two ways that break criterion 1 for JSON routes: a repeated
 * key inside an object (a single-field body such as {@code {"name":"A","name":"B"}} ended as a
 * 500, the multi-field sample body silently took the last value) and text after a complete
 * object ({@code {"name":"x"} garbage} was accepted and created the patient). Both are now
 * parse errors (see {@code spring.jackson} in application.yml), so they are the same 400
 * "Malformed request body." as every other unreadable body:
 * {@link #aRepeatedKeyIsMalformedNotAnInternalErrorAndNothingChanges},
 * {@link #textAfterACompleteObjectIsMalformedAndNothingChanges}; the ordinary valid bodies still
 * pass: {@link #validBodiesWithUnknownFieldsAndSurroundingWhitespaceStillWork}.
 */
class Spec2_7BadRequestAcceptanceTest extends AbstractSpecAcceptanceTest {

    @Autowired
    PatientRepository patientRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    SampleRepository sampleRepository;

    // @regression
    @ParameterizedTest(name = "POST {0} with {1}")
    @MethodSource("brokenBodies")
    void aBrokenBodyIsRejectedAsMalformedAndNothingIsCreated(String path, String kind, String body) throws Exception {
        int patients = storedRecords(patientRepository);
        int orders = storedRecords(orderRepository);
        int samples = sampleRepository.findAll().size();

        MvcResult result = mockMvc.perform(keyedPost(path, body)).andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", path);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET);
        assertThat(storedRecords(patientRepository)).as("patients stored").isEqualTo(patients);
        assertThat(storedRecords(orderRepository)).as("orders stored").isEqualTo(orders);
        assertThat(sampleRepository.findAll()).as("samples stored").hasSize(samples);
    }

    // ---- the two lenient-parser cases: a repeated key, text after the object -----------------

    /** Each route with a body that names one of its own fields twice, the second time with a different value. */
    static Stream<Arguments> repeatedKeyBodies() {
        return Stream.of(
                Arguments.of("POST", "/api/patients",
                        "{\"name\": \"" + SECRET + "-1\", \"name\": \"" + SECRET + "-2\"}"),
                Arguments.of("POST", "/api/orders",
                        "{\"patientId\": \"" + SECRET + "-1\", \"patientId\": \"" + SECRET + "-2\"}"),
                Arguments.of("POST", "/api/samples",
                        "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"sampleType\": \"SALIVA\"}"),
                Arguments.of("PUT", "/api/samples/{id}/status",
                        "{\"status\": \"READY_FOR_PROCESSING\", \"status\": \"READY_FOR_PROCESSING\"}"));
    }

    /** Each route with a complete, valid body followed by more content. */
    static Stream<Arguments> trailingContentBodies() {
        String patient = "{\"name\": \"" + SECRET + "\"}";
        String order = "{\"patientId\": \"PAT-1001\", \"note\": \"" + SECRET + "\"}";
        String sample = "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"note\": \"" + SECRET + "\"}";
        String status = "{\"status\": \"READY_FOR_PROCESSING\"}";
        return Stream.of(
                Arguments.of("POST", "/api/patients", "text after the object", patient + " garbage"),
                Arguments.of("POST", "/api/patients", "a second object",
                        patient + "{\"name\": \"" + SECRET + "-2\"}"),
                Arguments.of("POST", "/api/orders", "text after the object", order + " garbage"),
                Arguments.of("POST", "/api/orders", "a second object", order + order),
                Arguments.of("POST", "/api/samples", "text after the object", sample + " garbage"),
                Arguments.of("POST", "/api/samples", "a second object", sample + sample),
                Arguments.of("PUT", "/api/samples/{id}/status", "text after the object", status + " garbage"),
                Arguments.of("PUT", "/api/samples/{id}/status", "a second object", status + status));
    }

    // @regression
    @ParameterizedTest(name = "{0} {1} with a repeated key")
    @MethodSource("repeatedKeyBodies")
    void aRepeatedKeyIsMalformedNotAnInternalErrorAndNothingChanges(String method, String routeTemplate, String body)
            throws Exception {
        String sampleId = routeTemplate.contains("{id}") ? aSampleAtAccessioned() : null;
        Snapshot before = Snapshot.of(this);

        Outcome outcome = send(method, routeTemplate, sampleId, body);

        expectMalformedAndNothingChanged(outcome, before, sampleId);
    }

    // @regression
    @ParameterizedTest(name = "{0} {1} with {2}")
    @MethodSource("trailingContentBodies")
    void textAfterACompleteObjectIsMalformedAndNothingChanges(String method, String routeTemplate, String kind,
                                                               String body) throws Exception {
        String sampleId = routeTemplate.contains("{id}") ? aSampleAtAccessioned() : null;
        Snapshot before = Snapshot.of(this);

        Outcome outcome = send(method, routeTemplate, sampleId, body);

        expectMalformedAndNothingChanged(outcome, before, sampleId);
    }

    /** The positive control: the strictness above must not reject ordinary requests. */
    // @regression
    @Test
    void validBodiesWithUnknownFieldsAndSurroundingWhitespaceStillWork() throws Exception {
        String marker = "Control " + UUID.randomUUID();

        String patient = mockMvc.perform(keyedPost("/api/patients",
                        "\n  {\"name\": \"" + marker + "\",\n   \"unknown\": {\"a\": [1, 2]}}  \r\n\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String patientId = read(patient, "$.id");

        mockMvc.perform(keyedPost("/api/orders",
                        " {\"patientId\": \"" + patientId + "\", \"unknown\": true}\n"))
                .andExpect(status().isCreated());

        String sample = mockMvc.perform(keyedPost("/api/samples",
                        "\n{\"patientId\": \"" + patientId + "\", \"sampleType\": \"BLOOD\", \"unknown\": 7}\n\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sampleId = read(sample, "$.id");

        mockMvc.perform(withKey(put("/api/samples/" + sampleId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\n {\"status\": \"READY_FOR_PROCESSING\", \"unknown\": null} \n"))
                .andExpect(status().isOk());
        assertThat((String) read(getOk("/api/samples/" + sampleId), "$.status")).isEqualTo("READY_FOR_PROCESSING");
    }

    // ---- helpers for the two cases above -------------------------------------------------------

    /** A sample of a fresh patient, still at ACCESSIONED; returns its ID. */
    private String aSampleAtAccessioned() throws Exception {
        String patient = mockMvc.perform(keyedPost("/api/patients", "{\"name\": \"Owner " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read(registerSample(read(patient, "$.id"), "BLOOD"), "$.id");
    }

    /** What the response was, and every log event at ERROR level the server wrote while answering. */
    private record Outcome(MvcResult result, List<ILoggingEvent> errorLogs) { }

    /** Sends the request while recording every ERROR the application logs (a client error must not log one). */
    private Outcome send(String method, String routeTemplate, String sampleId, String body) throws Exception {
        String path = routeTemplate.replace("{id}", String.valueOf(sampleId));
        MockHttpServletRequestBuilder request = method.equals("PUT")
                ? withKey(put(path)).contentType(MediaType.APPLICATION_JSON).content(body)
                : keyedPost(path, body);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            MvcResult result = mockMvc.perform(request).andReturn();
            List<ILoggingEvent> errors = appender.list.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                    .toList();
            return new Outcome(result, errors);
        } finally {
            root.detachAppender(appender);
        }
    }

    private void expectMalformedAndNothingChanged(Outcome outcome, Snapshot before, String sampleId) throws Exception {
        MvcResult result = outcome.result();
        expectError(result, 400, "Bad Request", "Malformed request body.", result.getRequest().getRequestURI());
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET);
        assertThat(outcome.errorLogs()).as("ERROR log events for a client mistake").isEmpty();
        assertThat(Snapshot.of(this)).as("what the lab holds").isEqualTo(before);
        if (sampleId != null) {
            assertThat((String) read(getOk("/api/samples/" + sampleId), "$.status"))
                    .as("the sample's stage").isEqualTo("ACCESSIONED");
        }
    }

    /** How many patients, orders and samples the lab holds. */
    private record Snapshot(int patients, int orders, int samples) {
        static Snapshot of(Spec2_7BadRequestAcceptanceTest test) {
            return new Snapshot(storedRecords(test.patientRepository), storedRecords(test.orderRepository),
                    test.sampleRepository.findAll().size());
        }
    }

    /** Would be echoed back if the error body leaked the payload. */
    private static final String SECRET = "Secret-" + UUID.randomUUID();

    /**
     * For each create route: a complete, valid body, and the ways of breaking it. The valid body
     * is the yardstick: if a parser setting ever became lenient (single quotes, unquoted names),
     * the broken form would be understood as the valid one and something would be created.
     */
    static Stream<Arguments> brokenBodies() {
        record Route(String path, String validBody, String wrongShape) { }
        List<Route> routes = List.of(
                new Route("/api/patients",
                        "{\"name\": \"" + SECRET + "\"}",
                        "{\"name\": {\"first\": \"" + SECRET + "\"}}"),
                new Route("/api/orders",
                        "{\"patientId\": \"PAT-1001\", \"note\": \"" + SECRET + "\"}",
                        "{\"patientId\": {\"id\": \"PAT-1001\"}}"),
                new Route("/api/samples",
                        "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"note\": \"" + SECRET + "\"}",
                        "{\"patientId\": \"PAT-1001\", \"sampleType\": [\"BLOOD\"]}"));
        return routes.stream().flatMap(route -> Stream.of(
                Arguments.of(route.path(), "truncated JSON",
                        route.validBody().substring(0, route.validBody().length() - 1)),
                Arguments.of(route.path(), "single-quoted JSON",
                        route.validBody().replace('"', '\'')),
                Arguments.of(route.path(), "unquoted field names",
                        route.validBody().replaceAll("\"(\\w+)\":", "$1:")),
                Arguments.of(route.path(), "an array instead of an object",
                        "[" + route.validBody() + "]"),
                Arguments.of(route.path(), "plain text, not JSON",
                        "name=" + SECRET + "&x=1"),
                Arguments.of(route.path(), "a value of the wrong shape",
                        route.wrongShape())));
    }
}
