package com.labflow.client.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.labflow.common.model.ApiError;
import io.restassured.http.Header;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One answer from the lab, copied out of RestAssured so nothing downstream depends on its
 * types: the status, the headers, the typed body (for a success) and the lab's error body
 * (for a refusal).
 *
 * <p>Nothing here ever fails on a "wrong" status: a refusal is an ordinary outcome that
 * the caller inspects. {@link #bodyOrFail(int)} is the way to say "I expect this status,
 * give me the body or fail loudly".
 *
 * @param <T> the type of the success body
 */
public final class LabResponse<T> {

    private final String method;
    private final String path;
    private final JavaType bodyType;
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final T body;
    private final ApiError error;

    private LabResponse(String method, String path, JavaType bodyType, int statusCode,
                        Map<String, List<String>> headers, T body, ApiError error) {
        this.method = method;
        this.path = path;
        this.bodyType = bodyType;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.error = error;
    }

    /**
     * Wraps a RestAssured answer.
     *
     * @param response the raw answer
     * @param method   the HTTP method that was sent, for messages (for example {@code POST})
     * @param path     the request path, for messages (for example {@code /api/patients})
     * @param bodyType what a success body should be read as
     */
    public static <T> LabResponse<T> from(Response response, String method, String path, Class<T> bodyType) {
        return from(response, method, path, LabJson.mapper().constructType(bodyType));
    }

    /**
     * Same as {@link #from(Response, String, String, Class)}, for a success body that is a
     * generic type such as a bare JSON array: {@code new TypeReference<List<Sample>>() { }}.
     */
    public static <T> LabResponse<T> from(Response response, String method, String path, TypeReference<T> bodyType) {
        return from(response, method, path, LabJson.mapper().constructType(bodyType));
    }

    private static <T> LabResponse<T> from(Response response, String method, String path, JavaType bodyType) {
        int status = response.getStatusCode();
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header header : response.getHeaders()) {
            headers.computeIfAbsent(header.getName(), name -> new ArrayList<>()).add(header.getValue());
        }
        headers.replaceAll((name, values) -> List.copyOf(values));

        String raw = response.asString();
        boolean success = status >= 200 && status < 300;
        T body = success ? readOrNull(raw, bodyType) : null;
        ApiError error = success ? null : readErrorOrNull(raw);
        return new LabResponse<>(method, path, bodyType, status, Collections.unmodifiableMap(headers), body, error);
    }

    public int statusCode() {
        return statusCode;
    }

    /** The first value of a response header; the name is matched case-insensitively. */
    public Optional<String> header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /** The success body; present only for a 2xx answer whose body could be read as the expected type. */
    public Optional<T> body() {
        return Optional.ofNullable(body);
    }

    /** The lab's error body; present only when the answer's body parses as the lab's error body. */
    public Optional<ApiError> error() {
        return Optional.ofNullable(error);
    }

    /** The lab's own explanation of a refusal, or an empty string when there is none. */
    public String message() {
        return error == null ? "" : error.message();
    }

    /**
     * One line for messages, for example {@code POST /api/patients -> 409 "message"} (or
     * {@code POST /api/patients -> 201} when there is no lab message). It never includes
     * headers, request bodies or names.
     */
    public String describe() {
        return method + " " + path + " -> " + statusCode + quotedMessage();
    }

    /**
     * The success body of an answer that must have exactly the given status.
     *
     * @throws UnexpectedResponseError if the status differs, or the status matches but the
     *                                 body is missing or cannot be read as the expected type
     */
    public T bodyOrFail(int expectedStatus) {
        if (statusCode != expectedStatus) {
            throw new UnexpectedResponseError(method + " " + path + " expected " + expectedStatus
                    + " but the lab answered " + statusCode + quotedMessage());
        }
        return body().orElseThrow(() -> new UnexpectedResponseError(method + " " + path + " expected "
                + expectedStatus + " and the lab answered " + statusCode
                + ", but its body is missing or is not a " + typeName()));
    }

    /** {@code Patient}, or {@code list of Sample} for a list, for messages. */
    private String typeName() {
        return bodyType.isCollectionLikeType()
                ? "list of " + bodyType.getContentType().getRawClass().getSimpleName()
                : bodyType.getRawClass().getSimpleName();
    }

    private String quotedMessage() {
        String message = message();
        return message.isEmpty() ? "" : " \"" + message + "\"";
    }

    private static <T> T readOrNull(String raw, JavaType type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LabJson.mapper().readValue(raw, type);
        } catch (Exception notReadable) {
            return null;
        }
    }

    /** Only a JSON object that carries a message counts as the lab's error body. */
    private static ApiError readErrorOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = LabJson.mapper().readTree(raw);
            if (tree == null || !tree.isObject() || !tree.hasNonNull("message")) {
                return null;
            }
            return LabJson.mapper().treeToValue(tree, ApiError.class);
        } catch (Exception notAnErrorBody) {
            return null;
        }
    }
}
