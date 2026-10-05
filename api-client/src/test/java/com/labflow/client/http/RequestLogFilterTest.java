package com.labflow.client.http;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the run log line written for every request (functional-spec.md §2.6,
 * technical-considerations.md §2.8): method, path, status, time and identifiers; never a
 * header, the query string, the access key or a patient name; bodies only when asked for,
 * and then masked. Requests go to a {@link StubLab} with canned answers, and the
 * {@code lab.http} logger is captured with a Logback {@link ListAppender}.
 *
 * @spec: 005-reliable-isolated-execution
 */
class RequestLogFilterTest {

    /** A distinctive key that must never appear in the log. */
    private static final String SECRET = "s3cret-log-key-4417";
    private static final String PATIENT_NAME = "TEST-patient-Gus-31f0";

    private StubLab lab;
    private Logger logger;
    private Level previousLevel;
    private boolean previousAdditive;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        lab = StubLab.start();
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.LOGGER_NAME);
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        // Captured here only, so the unit-test console stays quiet.
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreTheLog() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
        lab.close();
    }

    private RequestSpecification labSpec() {
        return RestAssured.given().spec(LabRequestSpecs.create(lab.baseUrl(), new ApiKey(SECRET)));
    }

    /** A plain JSON request with only the filter under test, so the body setting is explicit. */
    private RequestSpecification specWith(RequestLogFilter filter) {
        return RestAssured.given().baseUri(lab.baseUrl()).contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + SECRET).filter(filter);
    }

    private List<String> lines(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> allLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertNoSecretsLogged() {
        assertThat(allLines()).isNotEmpty().allSatisfy(line -> assertThat(line)
                .doesNotContain(SECRET).doesNotContain("Bearer").doesNotContainIgnoringCase("authorization")
                .doesNotContain(PATIENT_NAME));
    }

    @Test
    // @regression
    @DisplayName("Every specification the client builds carries the request log filter")
    void labSpecsCarryTheFilter() {
        FilterableRequestSpecification spec = (FilterableRequestSpecification)
                SpecificationQuerier.query(LabRequestSpecs.create("http://127.0.0.1:1", new ApiKey(SECRET)));

        assertThat(spec.getDefinedFilters()).hasAtLeastOneElementOfType(RequestLogFilter.class);
    }

    @Test
    // @regression
    @DisplayName("A registered sample logs one INFO line with method, path, status, time and all three identifiers")
    void oneLineWithAllIdentifiers() {
        lab.answer("POST", "/api/samples", 201,
                "{\"id\":\"S-1\",\"patientId\":\"P-1\",\"orderId\":\"O-1\",\"sampleType\":\"BLOOD\","
                        + "\"order\":{\"id\":\"O-1\",\"patient\":{\"name\":\"" + PATIENT_NAME + "\"}}}");

        labSpec().body(Map.of("orderId", "O-1", "sampleType", "BLOOD")).post("/api/samples");

        assertThat(lines(Level.INFO)).singleElement().asString()
                .matches("POST /api/samples -> 201 in \\d+ ms \\[patient=P-1 order=O-1 sample=S-1]");
    }

    @Test
    // @regression
    @DisplayName("A patient answer logs the patient identifier but never the name, the key or any header")
    void patientNameKeyAndHeadersAreNeverLogged() {
        lab.answer("GET", "/api/patients/P-7", 200, "{\"id\":\"P-7\",\"name\":\"" + PATIENT_NAME + "\"}",
                Map.of("X-Echo", SECRET));

        labSpec().header("X-Trace", "Bearer " + SECRET).get("/api/patients/{id}", "P-7");

        // The key really was sent, so its absence from the log is meaningful.
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + SECRET);
        assertThat(lines(Level.INFO)).singleElement().asString()
                .matches("GET /api/patients/P-7 -> 200 in \\d+ ms \\[patient=P-7]");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("The path is logged without host or query string, with the identifier taken from the path")
    void pathWithoutHostOrQuery() {
        lab.answer("GET", "/api/samples/S-9/progress", 200, "{\"stages\":[]}");

        labSpec().queryParam("token", SECRET).get("/api/samples/S-9/progress");

        assertThat(lines(Level.INFO)).singleElement().asString()
                .matches("GET /api/samples/S-9/progress -> 200 in \\d+ ms \\[sample=S-9]")
                .doesNotContain("127.0.0.1").doesNotContain("?").doesNotContain("token");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("A request with no identifiers logs no bracket, and a refusal logs its status")
    void noIdentifiersAndRefusal() {
        lab.answer("GET", "/api/samples", 200, "[{\"id\":\"S-1\"}]");

        labSpec().get("/api/samples");
        labSpec().get("/api/orders/O-404");

        assertThat(lines(Level.INFO)).hasSize(2);
        assertThat(lines(Level.INFO).get(0)).matches("GET /api/samples -> 200 in \\d+ ms");
        assertThat(lines(Level.INFO).get(1)).matches("GET /api/orders/O-404 -> 404 in \\d+ ms \\[order=O-404]");
    }

    @Test
    // @regression
    @DisplayName("Bodies are not logged when body logging is off")
    void bodiesAreOffWhenNotAskedFor() {
        lab.answer("POST", "/api/patients", 201, "{\"id\":\"P-2\",\"name\":\"" + PATIENT_NAME + "\"}");

        specWith(new RequestLogFilter(false)).body(Map.of("name", PATIENT_NAME)).post("/api/patients");

        assertThat(lines(Level.DEBUG)).isEmpty();
        assertThat(allLines()).singleElement().asString().startsWith("POST /api/patients -> 201");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("With body logging on, both bodies are logged at DEBUG with the name masked")
    void bodiesAreLoggedMaskedWhenAskedFor() {
        lab.answer("POST", "/api/patients", 201, "{\"id\":\"P-3\",\"name\":\"" + PATIENT_NAME + "\"}");

        specWith(new RequestLogFilter(true)).body(Map.of("name", PATIENT_NAME)).post("/api/patients");

        // The name really was sent and answered, so masking it is meaningful.
        assertThat(lab.lastRequest().body()).contains(PATIENT_NAME);
        assertThat(lines(Level.INFO)).singleElement().asString()
                .matches("POST /api/patients -> 201 in \\d+ ms \\[patient=P-3]");
        assertThat(lines(Level.DEBUG)).containsExactly(
                "  request body: {\"name\":\"***\"}",
                "  response body: {\"id\":\"P-3\",\"name\":\"***\"}");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("With body logging on, a malformed request body is logged only as its length")
    void malformedBodyIsLoggedAsItsLength() {
        String malformed = "{\"name\":\"" + PATIENT_NAME + "\"";

        specWith(new RequestLogFilter(true)).body(malformed).post("/api/patients");

        assertThat(lines(Level.DEBUG)).contains("  request body: <non-JSON body, " + malformed.length() + " chars>");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("When the lab does not answer, one line says so and the failure still reaches the caller")
    void noAnswerIsLoggedAndRethrown() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> RestAssured.given()
                .spec(LabRequestSpecs.create("http://127.0.0.1:" + closedPort, new ApiKey(SECRET)))
                .get("/api/patients/{id}", "P-1"))
                .isInstanceOf(java.net.ConnectException.class);

        assertThat(lines(Level.INFO)).singleElement().asString()
                .matches("GET /api/patients/P-1 -> no answer \\(\\w*ConnectException\\)");
        assertNoSecretsLogged();
    }

    @Test
    // @regression
    @DisplayName("Body logging comes from -DlogBodies, then LOG_BODIES, and is off by default")
    void bodySettingPrecedence() {
        Map<String, String> none = Map.of();

        assertThat(RequestLogFilter.logBodies(none::get, none::get)).isFalse();
        assertThat(RequestLogFilter.logBodies(none::get, Map.of("LOG_BODIES", "TRUE")::get)).isTrue();
        assertThat(RequestLogFilter.logBodies(Map.of("logBodies", "true")::get, none::get)).isTrue();
        assertThat(RequestLogFilter.logBodies(Map.of("logBodies", "false")::get,
                Map.of("LOG_BODIES", "true")::get)).isFalse();
        assertThat(RequestLogFilter.logBodies(Map.of("logBodies", "  ")::get,
                Map.of("LOG_BODIES", " true ")::get)).isTrue();
        assertThat(RequestLogFilter.logBodies(Map.of("logBodies", "yes")::get, none::get)).isFalse();
    }
}
