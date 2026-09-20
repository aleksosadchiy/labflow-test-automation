package com.labflow.client.preflight;

import com.labflow.common.config.LabConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The one-off "is the lab there, and does it accept my key?" check, run once before any
 * API check. Without it, a lab that is not running (or a wrong key) shows up as dozens of
 * confusing connection errors or 401s, one per check. With it, the whole run stops before
 * the first check with one clear message that names the address and says what to do.
 *
 * <p>Two requests, in this order:
 * <ol>
 *   <li>{@code GET /health}, sent <em>without</em> the key (the endpoint is open). Anything
 *       but a 200 (connection refused, timeout, 503 while the lab is still starting, a
 *       404 from some other program on that port) means "no lab here".</li>
 *   <li>{@code GET /api/samples} with the key. 401 means the key was rejected; any other
 *       non-200 is an unexpected answer. The key is never sent if step 1 failed.</li>
 * </ol>
 *
 * <p>It uses the JDK's own {@link HttpClient} rather than RestAssured: it starts fast (no
 * Groovy warm-up) and the check must stay cheap. There are no retries and no sleeps, so a
 * dead lab is reported within the connect timeout. Messages never contain the key,
 * headers or response bodies.
 */
public final class LabPreflight {

    /** Greppable prefix of every failure message. */
    public static final String BANNER = "LAB PRE-FLIGHT FAILED:";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String START_COMMAND = "cd mock-lab-app && ./mvnw spring-boot:run";

    private LabPreflight() {
    }

    /**
     * Verifies that the lab is reachable and accepts the configured key.
     *
     * @throws LabPreflightException if it is not; the message says what is wrong and what to do
     */
    public static void check(LabConfig config) {
        String baseUrl = config.baseUrl();
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // plain HTTP/1.1: no HTTP/2 upgrade handshake against a plain-HTTP lab
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        int health = get(http, baseUrl, "/health", null);
        if (health != 200) {
            throw new LabPreflightException(unreachable(baseUrl, " (health check answered " + health + ")"));
        }

        int samples = get(http, baseUrl, "/api/samples", "Bearer " + config.apiKey().reveal());
        if (samples == 401) {
            throw new LabPreflightException(BANNER + " the lab at " + baseUrl
                    + " rejected the access key (set the API_TOKEN environment variable)");
        }
        if (samples != 200) {
            throw new LabPreflightException(BANNER + " unexpected answer " + samples
                    + " from GET /api/samples at " + baseUrl);
        }
    }

    /** Sends one GET and returns only the status code; the body is discarded unread. */
    private static int get(HttpClient http, String baseUrl, String path, String authorization) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .GET();
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | IllegalArgumentException e) {
            // connection refused, timeout, or an address the JDK cannot use (for example no scheme)
            throw new LabPreflightException(unreachable(baseUrl, ""), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LabPreflightException(BANNER + " interrupted while checking the lab at " + baseUrl, e);
        }
    }

    private static String unreachable(String baseUrl, String detail) {
        return BANNER + " cannot reach the lab at " + baseUrl + detail + ". Start it: " + START_COMMAND;
    }
}
