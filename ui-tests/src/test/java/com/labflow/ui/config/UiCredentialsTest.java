package com.labflow.ui.config;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UiCredentials} (tech spec §2.2): the username and password can be
 * supplied through the environment, the password never through a system property, the
 * lab's local defaults apply otherwise, and the password never shows up when the
 * credentials are printed. The lookups are fed from small maps, so the real process
 * environment is never touched.
 *
 * @spec: 004-ui-automation-page-objects
 */
class UiCredentialsTest {

    /** A distinctive value that must never appear in any printed form. */
    private static final String SECRET = "s3cret-ui-pass-5531";

    private static UiCredentials load(Map<String, String> systemProperties, Map<String, String> environment) {
        return UiCredentials.load(systemProperties::get, environment::get);
    }

    // ---- the defaults ----

    @Test
    // @regression
    @DisplayName("The local defaults are the lab's documented username and password")
    void defaultValuesAreTheLabsLocalOnes() {
        assertThat(UiCredentials.DEFAULT_USERNAME).isEqualTo("LabTech");
        assertThat(UiCredentials.DEFAULT_PASSWORD).isEqualTo("I_Like_This_Lab");
    }

    @Test
    // @regression
    @DisplayName("With nothing supplied, the lab's local username and password are used")
    void nothingSuppliedUsesTheDefaults() {
        UiCredentials credentials = load(Map.of(), Map.of());

        assertThat(credentials.username()).isEqualTo("LabTech");
        assertThat(credentials.password().reveal()).isEqualTo("I_Like_This_Lab");
    }

    // ---- from the environment ----

    @Test
    // @regression
    @DisplayName("MOCK_LAB_USERNAME and MOCK_LAB_PASSWORD beat the defaults")
    void environmentBeatsTheDefaults() {
        UiCredentials credentials = load(Map.of(),
                Map.of("MOCK_LAB_USERNAME", "OtherTech", "MOCK_LAB_PASSWORD", SECRET));

        assertThat(credentials.username()).isEqualTo("OtherTech");
        assertThat(credentials.password().reveal()).isEqualTo(SECRET);
    }

    @Test
    // @regression
    @DisplayName("Username and password are resolved independently of each other")
    void usernameAndPasswordAreIndependent() {
        assertThat(load(Map.of(), Map.of("MOCK_LAB_USERNAME", "OtherTech")).password().reveal())
                .isEqualTo("I_Like_This_Lab");
        assertThat(load(Map.of(), Map.of("MOCK_LAB_PASSWORD", SECRET)).username())
                .isEqualTo("LabTech");
    }

    @Test
    // @regression
    @DisplayName("A blank or whitespace-only value counts as unset, so the default is used")
    void blankValuesFallBackToTheDefaults() {
        for (String blank : new String[] {"", "   ", "\t\n"}) {
            UiCredentials credentials = load(Map.of(),
                    Map.of("MOCK_LAB_USERNAME", blank, "MOCK_LAB_PASSWORD", blank));

            assertThat(credentials.username()).as("username for a blank value").isEqualTo("LabTech");
            assertThat(credentials.password().reveal()).as("password for blank value")
                    .isEqualTo("I_Like_This_Lab");
        }
    }

    // ---- never from a system property ----

    @Test
    // @regression
    @DisplayName("A password system property of any name is ignored: it is never read from the command line")
    void passwordSystemPropertyIsIgnored() {
        UiCredentials credentials = load(
                Map.of("MOCK_LAB_PASSWORD", SECRET, "mockLabPassword", SECRET, "password", SECRET),
                Map.of());

        assertThat(credentials.password().reveal()).isEqualTo("I_Like_This_Lab");
    }

    @Test
    // @regression
    @DisplayName("With a password system property also set, the environment variable still wins")
    void environmentWinsOverAPasswordSystemProperty() {
        UiCredentials credentials = load(
                Map.of("MOCK_LAB_PASSWORD", SECRET, "mockLabPassword", SECRET),
                Map.of("MOCK_LAB_PASSWORD", "env-pass-1234"));

        assertThat(credentials.password().reveal()).isEqualTo("env-pass-1234");
    }

    @Test
    // @regression
    @DisplayName("The system-property lookup is never consulted at all")
    void systemPropertiesAreNeverConsulted() {
        UiCredentials credentials = UiCredentials.load(
                name -> {
                    throw new AssertionError("system property looked up: " + name);
                },
                Map.<String, String>of()::get);

        assertThat(credentials.username()).isEqualTo("LabTech");
    }

    // ---- the password never shows up ----

    @Test
    // @regression
    @DisplayName("UiCredentials.toString(), String.valueOf and concatenation never contain the password")
    void toStringNeverContainsThePassword() {
        UiCredentials credentials = load(Map.of(),
                Map.of("MOCK_LAB_USERNAME", "OtherTech", "MOCK_LAB_PASSWORD", SECRET));

        assertThat(credentials.toString()).doesNotContain(SECRET)
                .isEqualTo("UiCredentials[username=OtherTech, password=***]");
        assertThat(String.valueOf(credentials)).doesNotContain(SECRET);
        assertThat("credentials=" + credentials).doesNotContain(SECRET);
        assertThat(String.format("%s", credentials)).doesNotContain(SECRET);
    }
}
