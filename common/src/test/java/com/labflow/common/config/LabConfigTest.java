package com.labflow.common.config;

// @layer: unit
// @spec: 003-test-automation-foundation

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link LabConfig} and {@link ApiKey}, per functional-spec.md §2.3
 * (Reaching the Lab): the address and key can be supplied from outside the project, the
 * key only through the environment, the lab's local defaults apply otherwise, and the key
 * value never shows up when the configuration is printed. The two lookups are fed from
 * small maps, so the real process environment is never touched.
 *
 * @spec: 003-test-automation-foundation
 */
class LabConfigTest {

    /** A distinctive value that must never appear in any printed form. */
    private static final String SECRET = "s3cret-test-key-8842";

    private static LabConfig load(Map<String, String> systemProperties, Map<String, String> environment) {
        return LabConfig.load(systemProperties::get, environment::get);
    }

    // ---- the defaults ----

    @Test
    // @regression
    @DisplayName("The standard local defaults are the lab's address and its documented local key")
    void defaultValuesAreTheLabsLocalOnes() {
        assertEquals("http://localhost:8080", LabConfig.DEFAULT_BASE_URL);
        assertEquals("mock-lab-local-token", LabConfig.DEFAULT_API_KEY);
    }

    @Test
    // @regression
    @DisplayName("With nothing supplied, the standard local address and key are used")
    void nothingSuppliedUsesTheDefaults() {
        LabConfig config = load(Map.of(), Map.of());

        assertEquals("http://localhost:8080", config.baseUrl());
        assertEquals("mock-lab-local-token", config.apiKey().reveal());
    }

    // ---- lab address ----

    @Test
    // @regression
    @DisplayName("The baseUrl system property beats the BASE_URL environment variable")
    void systemPropertyBeatsEnvironmentForTheAddress() {
        LabConfig config = load(
                Map.of("baseUrl", "http://from-property:1"),
                Map.of("BASE_URL", "http://from-env:2"));

        assertEquals("http://from-property:1", config.baseUrl());
    }

    @Test
    // @regression
    @DisplayName("The BASE_URL environment variable beats the default address")
    void environmentBeatsDefaultForTheAddress() {
        LabConfig config = load(Map.of(), Map.of("BASE_URL", "http://from-env:2"));

        assertEquals("http://from-env:2", config.baseUrl());
    }

    @Test
    // @regression
    @DisplayName("A blank or whitespace-only baseUrl property counts as unset and falls through to BASE_URL")
    void blankPropertyFallsThroughToEnvironment() {
        assertEquals("http://from-env:2",
                load(Map.of("baseUrl", ""), Map.of("BASE_URL", "http://from-env:2")).baseUrl());
        assertEquals("http://from-env:2",
                load(Map.of("baseUrl", "   \t"), Map.of("BASE_URL", "http://from-env:2")).baseUrl());
    }

    @Test
    // @regression
    @DisplayName("A blank or whitespace-only BASE_URL counts as unset and falls through to the default")
    void blankEnvironmentFallsThroughToDefault() {
        assertEquals("http://localhost:8080", load(Map.of(), Map.of("BASE_URL", "")).baseUrl());
        assertEquals("http://localhost:8080", load(Map.of(), Map.of("BASE_URL", "  ")).baseUrl());
        assertEquals("http://localhost:8080",
                load(Map.of("baseUrl", " "), Map.of("BASE_URL", " ")).baseUrl());
    }

    @Test
    // @regression
    @DisplayName("A trailing slash is stripped from the address")
    void trailingSlashIsStripped() {
        assertEquals("http://localhost:18080",
                load(Map.of("baseUrl", "http://localhost:18080/"), Map.of()).baseUrl());
        assertEquals("http://localhost:18080",
                load(Map.of(), Map.of("BASE_URL", "http://localhost:18080/")).baseUrl());
    }

    @Test
    // @regression
    @DisplayName("Several trailing slashes are all stripped, but slashes inside the address are kept")
    void severalTrailingSlashesAreStrippedAndInnerOnesKept() {
        assertEquals("http://localhost:18080",
                load(Map.of("baseUrl", "http://localhost:18080///"), Map.of()).baseUrl());
        assertEquals("http://host:1/lab",
                load(Map.of("baseUrl", "http://host:1/lab/"), Map.of()).baseUrl());
    }

    @Test
    // @regression
    @DisplayName("Surrounding whitespace is trimmed from the address, before the trailing slash is stripped")
    void surroundingWhitespaceIsTrimmed() {
        assertEquals("http://localhost:18080",
                load(Map.of("baseUrl", "  http://localhost:18080/  "), Map.of()).baseUrl());
        assertEquals("http://localhost:18080",
                load(Map.of(), Map.of("BASE_URL", "\thttp://localhost:18080\n")).baseUrl());
    }

    // ---- access key ----

    @Test
    // @regression
    @DisplayName("The access key comes from the API_TOKEN environment variable")
    void keyComesFromTheEnvironment() {
        LabConfig config = load(Map.of(), Map.of("API_TOKEN", SECRET));

        assertEquals(SECRET, config.apiKey().reveal());
    }

    @Test
    // @regression
    @DisplayName("A blank or whitespace-only API_TOKEN counts as unset, so the default key is used")
    void blankTokenFallsBackToTheDefaultKey() {
        assertEquals("mock-lab-local-token", load(Map.of(), Map.of("API_TOKEN", "")).apiKey().reveal());
        assertEquals("mock-lab-local-token", load(Map.of(), Map.of("API_TOKEN", "  \t")).apiKey().reveal());
    }

    @Test
    // @regression
    @DisplayName("An apiToken system property is ignored: the key is never read from the command line")
    void apiTokenSystemPropertyIsIgnored() {
        LabConfig config = load(Map.of("apiToken", SECRET), Map.of());

        assertEquals("mock-lab-local-token", config.apiKey().reveal());
    }

    @Test
    // @regression
    @DisplayName("An API_TOKEN system property is ignored too, and the environment variable still wins")
    void upperCaseApiTokenSystemPropertyIsIgnored() {
        assertEquals("mock-lab-local-token",
                load(Map.of("API_TOKEN", SECRET), Map.of()).apiKey().reveal());
        assertEquals("env-key-1234",
                load(Map.of("apiToken", SECRET, "API_TOKEN", SECRET), Map.of("API_TOKEN", "env-key-1234"))
                        .apiKey().reveal());
    }

    @Test
    // @regression
    @DisplayName("The address and the key are resolved independently of each other")
    void addressAndKeyAreIndependent() {
        LabConfig config = load(
                Map.of("baseUrl", "http://from-property:1"),
                Map.of("API_TOKEN", SECRET));

        assertEquals("http://from-property:1", config.baseUrl());
        assertEquals(SECRET, config.apiKey().reveal());
    }

    // ---- the key never shows up ----

    @Test
    // @regression
    @DisplayName("ApiKey.toString() is exactly three asterisks")
    void apiKeyToStringIsMasked() {
        assertEquals("***", new ApiKey(SECRET).toString());
    }

    @Test
    // @regression
    @DisplayName("LabConfig.toString() and String.valueOf never contain the key")
    void labConfigToStringNeverContainsTheKey() {
        LabConfig config = load(Map.of("baseUrl", "http://localhost:18080"), Map.of("API_TOKEN", SECRET));

        assertFalse(config.toString().contains(SECRET), "toString leaked the key: " + config);
        assertFalse(String.valueOf(config).contains(SECRET), "String.valueOf leaked the key");
        assertFalse(("config=" + config).contains(SECRET), "concatenation leaked the key");
        assertFalse(String.format("%s", config).contains(SECRET), "String.format leaked the key");
        assertEquals("LabConfig[baseUrl=http://localhost:18080, apiKey=***]", config.toString());
    }

    @Test
    // @regression
    @DisplayName("reveal() returns the raw value, and it is the only way to get it")
    void revealReturnsTheRawValue() {
        ApiKey key = new ApiKey(SECRET);

        assertEquals(SECRET, key.reveal());
        assertNotEquals(SECRET, key.toString());
        assertNotEquals(SECRET, String.valueOf(key));
    }

    @Test
    // @regression
    @DisplayName("Keys with the same value are equal and have the same hash code; different values are not equal")
    void equalityFollowsTheValue() {
        assertEquals(new ApiKey(SECRET), new ApiKey(SECRET));
        assertEquals(new ApiKey(SECRET).hashCode(), new ApiKey(SECRET).hashCode());
        assertNotEquals(new ApiKey(SECRET), new ApiKey("another-key"));
        assertNotEquals(new ApiKey(SECRET), SECRET);
    }

    @Test
    // @regression
    @DisplayName("A null, empty or blank key is rejected, so a configuration with an empty key cannot exist")
    void blankKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKey(null));
        assertThrows(IllegalArgumentException.class, () -> new ApiKey(""));
        assertThrows(IllegalArgumentException.class, () -> new ApiKey("   "));
        assertThrows(IllegalArgumentException.class, () -> new ApiKey("\t\n"));
    }
}
