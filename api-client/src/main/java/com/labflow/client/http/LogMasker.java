package com.labflow.client.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a request or answer body into text that is safe to write to the run log.
 *
 * <p>A body that is JSON comes back as compact JSON with the value of every field named
 * {@code name} or {@code password} (matched ignoring case, at any depth, inside objects and
 * arrays) replaced by {@value #MASK}. Every other field is kept as it was.
 *
 * <p>A body that is not JSON (for example the deliberately malformed bodies of the
 * invalid-input checks, which may still contain a patient name) is never shown: it becomes
 * {@code <non-JSON body, N chars>}. An empty or {@code null} body becomes
 * {@value #EMPTY}.
 *
 * <p>Pure and stateless: safe to call from any thread, and it never throws.
 */
public final class LogMasker {

    /** What a hidden value is replaced with. */
    public static final String MASK = "***";

    /** What an empty or missing body is logged as. */
    public static final String EMPTY = "<empty body>";

    /** Field names whose values never reach the log. */
    private static final Set<String> MASKED_FIELDS = Set.of("name", "password");

    /** Strict reader: {@code {"a":1} trailing text} is not JSON, so it is not half-shown. */
    private static final ObjectReader STRICT_READER =
            LabJson.mapper().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private LogMasker() {
    }

    /**
     * @param body the raw body, possibly {@code null}
     * @return the masked JSON, {@code <non-JSON body, N chars>} or {@value #EMPTY}
     */
    public static String mask(String body) {
        if (body == null || body.isBlank()) {
            return EMPTY;
        }
        try {
            JsonNode tree = STRICT_READER.readTree(body);
            if (tree == null || tree.isMissingNode()) {
                return nonJson(body);
            }
            maskInPlace(tree);
            return LabJson.mapper().writeValueAsString(tree);
        } catch (Exception notJson) {
            return nonJson(body);
        }
    }

    private static void maskInPlace(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.properties().forEach(field -> names.add(field.getKey()));
            for (String fieldName : names) {
                if (MASKED_FIELDS.contains(fieldName.toLowerCase(Locale.ROOT))) {
                    object.put(fieldName, MASK);
                } else {
                    maskInPlace(object.get(fieldName));
                }
            }
        } else if (node.isArray()) {
            node.forEach(LogMasker::maskInPlace);
        }
    }

    private static String nonJson(String body) {
        return "<non-JSON body, " + body.length() + " chars>";
    }
}
