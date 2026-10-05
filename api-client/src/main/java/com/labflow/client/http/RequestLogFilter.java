package com.labflow.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes one line per request to the run log, on logger {@value #LOGGER_NAME}:
 * <pre>POST /api/samples -> 201 in 34 ms [patient=P-1 order=O-1 sample=S-1]</pre>
 * or, when the lab could not be reached,
 * <pre>GET /api/patients/p-1 -> no answer (ConnectException)</pre>
 *
 * <p>What it deliberately leaves out, because the run log must be safe to share:
 * <ul>
 *   <li><b>No headers at all</b>, so the {@code Authorization} header (the access key) can
 *       never appear, whatever other headers exist.</li>
 *   <li><b>No query string and no host</b>: only the path.</li>
 *   <li><b>No names</b>: identifiers come from the path segments
 *       ({@code /api/samples/{id}}) and from the answer's top-level {@code id},
 *       {@code patientId}, {@code orderId} and {@code sampleId} fields only. The
 *       {@code id} field is attributed to the resource of the path
 *       ({@code /api/patients} gives a patient, and so on).</li>
 * </ul>
 *
 * <p><b>Bodies</b> are written only when {@code logBodies} is on, at DEBUG (so they go to
 * the log file, not the console), and always through {@link LogMasker}.
 *
 * <p>The filter keeps no state between requests, so one instance is safe for any number of
 * threads.
 */
public final class RequestLogFilter implements Filter {

    /** The logger every request line is written to. */
    public static final String LOGGER_NAME = "lab.http";

    /** System property that turns body logging on ({@code -DlogBodies=true}). */
    public static final String LOG_BODIES_PROPERTY = "logBodies";

    /** Environment variable that turns body logging on when the system property is absent. */
    public static final String LOG_BODIES_ENV = "LOG_BODIES";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    /** The kinds of identifier, in the order they are written. */
    private static final String[] KINDS = {"patient", "order", "sample"};

    /** Path resource ({@code /api/<resource>}) to the kind of identifier it carries. */
    private static final Map<String, String> RESOURCE_KINDS =
            Map.of("patients", "patient", "orders", "order", "samples", "sample");

    /** Answer field to the kind of identifier it carries ({@code id} depends on the path). */
    private static final Map<String, String> FIELD_KINDS =
            Map.of("patientId", "patient", "orderId", "order", "sampleId", "sample");

    private final boolean logBodies;

    /**
     * @param logBodies whether to also write the masked request and answer bodies at DEBUG
     */
    public RequestLogFilter(boolean logBodies) {
        this.logBodies = logBodies;
    }

    /** A filter whose body setting comes from the real system properties and environment. */
    public static RequestLogFilter fromEnvironment() {
        return new RequestLogFilter(logBodies(System::getProperty, System::getenv));
    }

    /**
     * Resolves the body setting: the {@code -DlogBodies} system property, then the
     * {@code LOG_BODIES} environment variable, then {@code false}. A blank value counts as
     * unset; {@code true} (ignoring case and surrounding whitespace) turns it on, anything
     * else leaves it off. The lookups are passed in (each returns null when a name is unset)
     * so precedence can be unit-tested without touching the real process environment.
     */
    public static boolean logBodies(Function<String, String> systemProperties, Function<String, String> environment) {
        String value = systemProperties.apply(LOG_BODIES_PROPERTY);
        if (value == null || value.isBlank()) {
            value = environment.apply(LOG_BODIES_ENV);
        }
        return value != null && value.strip().equalsIgnoreCase("true");
    }

    /** Whether this filter writes bodies. */
    public boolean logsBodies() {
        return logBodies;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        String method = request.getMethod();
        String path = pathOf(request.getURI());

        long started = System.nanoTime();
        Response response;
        try {
            response = context.next(request, responseSpec);
        } catch (Exception noAnswer) {
            // RestAssured can throw checked exceptions (ConnectException) without declaring them.
            LOG.info("{} {} -> no answer ({})", method, path, noAnswer.getClass().getSimpleName());
            logRequestBody(request);
            throw noAnswer;
        }
        long millis = (System.nanoTime() - started) / 1_000_000;

        String body = response.asString();
        LOG.info("{} {} -> {} in {} ms{}", method, path, response.getStatusCode(), millis,
                identifiers(path, body));
        logRequestBody(request);
        if (logBodies) {
            LOG.debug("  response body: {}", LogMasker.mask(body));
        }
        return response;
    }

    /** Written after the request's line, so the bodies read as detail under it. */
    private void logRequestBody(FilterableRequestSpecification request) {
        if (logBodies) {
            LOG.debug("  request body: {}", LogMasker.mask(bodyOf(request.getBody())));
        }
    }

    /** The path of a full request URI, without host and query string. */
    static String pathOf(String uri) {
        try {
            String path = URI.create(uri).getRawPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (Exception notAUri) {
            // Never fall back to the raw text: it might carry a query string.
            return "<unreadable path>";
        }
    }

    /**
     * {@code " [patient=… order=… sample=…]"} with only the identifiers that are present, or
     * an empty string when there are none.
     */
    static String identifiers(String path, String responseBody) {
        Map<String, String> found = new LinkedHashMap<>();
        String[] segments = path.split("/");
        // "/api/samples/S-1/status" splits into "", "api", "samples", "S-1", "status".
        String resourceKind = segments.length > 2 && segments[1].equals("api")
                ? RESOURCE_KINDS.get(segments[2].toLowerCase(Locale.ROOT)) : null;
        if (resourceKind != null && segments.length > 3 && !segments[3].isEmpty()) {
            found.put(resourceKind, segments[3]);
        }

        JsonNode answer = readObjectOrNull(responseBody);
        if (answer != null) {
            if (resourceKind != null) {
                putIfText(found, resourceKind, answer.get("id"));
            }
            FIELD_KINDS.forEach((field, kind) -> putIfText(found, kind, answer.get(field)));
        }

        StringBuilder line = new StringBuilder();
        for (String kind : KINDS) {
            String id = found.get(kind);
            if (id != null) {
                line.append(line.length() == 0 ? " [" : " ").append(kind).append('=').append(id);
            }
        }
        return line.length() == 0 ? "" : line.append(']').toString();
    }

    private static void putIfText(Map<String, String> found, String kind, JsonNode value) {
        if (value != null && value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
            found.putIfAbsent(kind, value.asText());
        }
    }

    private static JsonNode readObjectOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = LabJson.mapper().readTree(body);
            return tree != null && tree.isObject() ? tree : null;
        } catch (Exception notJson) {
            return null;
        }
    }

    /** The request body as text; RestAssured holds it as a String or bytes once serialized. */
    private static String bodyOf(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body.toString();
    }
}
