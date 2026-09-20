package com.labflow.client.support;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny fake lab for the framework's own self-tests, built on the JDK's HTTP server so no
 * extra library is needed. It listens on 127.0.0.1 on an ephemeral port, records every
 * request it receives (method, path, headers, body) and answers each
 * {@code METHOD path} with whatever the test registered beforehand. An unregistered
 * request gets the lab's own 404 body, so a test that forgot a stub fails visibly.
 *
 * <p>Start one per test and stop it in {@code @AfterEach}:
 * <pre>{@code
 * StubLab lab = StubLab.start();
 * lab.answer("POST", "/api/patients", 201, "{\"id\":\"p-1\",\"name\":\"Ada\"}");
 * ... use lab.baseUrl() ...
 * lab.requests();   // what the code under test actually sent
 * lab.close();
 * }</pre>
 */
public final class StubLab implements AutoCloseable {

    /** One request as the stub received it. */
    public record ReceivedRequest(String method, String path, Map<String, List<String>> headers, String body) {

        /** The first value of a request header (name matched case-insensitively), if it was sent. */
        public Optional<String> header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
        }
    }

    /** One canned answer. */
    public record Answer(int status, String body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();

    private StubLab(HttpServer server) {
        this.server = server;
    }

    /** Starts a stub on a free port of the loopback interface. */
    public static StubLab start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            StubLab lab = new StubLab(server);
            server.createContext("/", lab::handle);
            server.start();
            return lab;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the stub lab", e);
        }
    }

    /** The address to give the code under test, for example {@code http://127.0.0.1:53211}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Answers {@code METHOD path} with a JSON body. */
    public StubLab answer(String method, String path, int status, String jsonBody) {
        return answer(method, path, status, jsonBody, Map.of());
    }

    /** Answers {@code METHOD path} with a JSON body and extra response headers. */
    public StubLab answer(String method, String path, int status, String jsonBody, Map<String, String> headers) {
        answers.put(key(method, path), new Answer(status, jsonBody, headers));
        return this;
    }

    /** Every request received so far, oldest first. */
    public List<ReceivedRequest> requests() {
        return List.copyOf(requests);
    }

    /** The most recent request; fails the calling test if nothing was received. */
    public ReceivedRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new AssertionError("The stub lab received no request");
        }
        return requests.get(requests.size() - 1);
    }

    /** Stops the stub and frees its port. */
    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();

            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new ReceivedRequest(method, path, headers, body));

            Answer answer = answers.getOrDefault(key(method, path), notFound(path));
            answer.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] payload = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
        }
    }

    private static Answer notFound(String path) {
        return new Answer(404, "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Resource not found.\","
                + "\"path\":\"" + path + "\",\"timestamp\":\"2026-01-01T00:00:00Z\"}", Map.of());
    }

    private static String key(String method, String path) {
        return method + " " + path;
    }
}
