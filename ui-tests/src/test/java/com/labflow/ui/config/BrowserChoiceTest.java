package com.labflow.ui.config;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BrowserChoice} (spec 005 functional-spec.md §2.5, tech spec §2.5 and
 * §2.7): the browser comes from {@code -Dbrowser}, then {@code BROWSER}, then Chromium; names
 * match ignoring case and surrounding whitespace; a blank value counts as unset; an unknown
 * name is rejected with a message naming the value and listing the supported browsers. The
 * lookups are fed from small maps, so the real process environment is never touched.
 *
 * @spec: 005-reliable-isolated-execution
 */
class BrowserChoiceTest {

    private static BrowserChoice load(Map<String, String> systemProperties, Map<String, String> environment) {
        return BrowserChoice.load(systemProperties::get, environment::get);
    }

    @Test
    // @regression
    @DisplayName("With nothing supplied, the screen checks run in Chromium")
    void nothingSuppliedUsesChromium() {
        assertThat(load(Map.of(), Map.of())).isEqualTo(BrowserChoice.CHROMIUM);
    }

    @Test
    // @regression
    @DisplayName("The BROWSER environment variable beats the default")
    void environmentBeatsTheDefault() {
        assertThat(load(Map.of(), Map.of("BROWSER", "webkit"))).isEqualTo(BrowserChoice.WEBKIT);
    }

    @Test
    // @regression
    @DisplayName("The -Dbrowser system property beats the BROWSER environment variable")
    void systemPropertyBeatsTheEnvironment() {
        assertThat(load(Map.of("browser", "webkit"), Map.of("BROWSER", "chromium"))).isEqualTo(BrowserChoice.WEBKIT);
    }

    @Test
    // @regression
    @DisplayName("A blank or whitespace-only value counts as unset, so the next source is used")
    void blankValuesCountAsUnset() {
        for (String blank : new String[] {"", "   ", "\t\n"}) {
            assertThat(load(Map.of("browser", blank), Map.of("BROWSER", "webkit")))
                    .as("system property '%s' falls through to BROWSER", blank).isEqualTo(BrowserChoice.WEBKIT);
            assertThat(load(Map.of("browser", blank), Map.of("BROWSER", blank)))
                    .as("both '%s' fall through to the default", blank).isEqualTo(BrowserChoice.CHROMIUM);
        }
    }

    @Test
    // @regression
    @DisplayName("Browser names match ignoring case and surrounding whitespace")
    void namesMatchIgnoringCaseAndWhitespace() {
        assertThat(load(Map.of("browser", "WebKit"), Map.of())).isEqualTo(BrowserChoice.WEBKIT);
        assertThat(load(Map.of(), Map.of("BROWSER", "WEBKIT"))).isEqualTo(BrowserChoice.WEBKIT);
        assertThat(load(Map.of("browser", "  Chromium \n"), Map.of())).isEqualTo(BrowserChoice.CHROMIUM);
    }

    @Test
    // @regression
    @DisplayName("An unsupported browser is rejected with a message naming it and listing the supported ones")
    void unknownBrowserIsRejectedWithAClearMessage() {
        assertThatThrownBy(() -> load(Map.of("browser", "opera"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported browser 'opera'. Supported: chromium, webkit");
        assertThatThrownBy(() -> load(Map.of(), Map.of("BROWSER", "/usr/bin/open-in-host")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported browser '/usr/bin/open-in-host'. Supported: chromium, webkit");
    }

    @Test
    // @regression
    @DisplayName("Firefox is no longer supported, so it is rejected like any unknown browser")
    void firefoxIsRejected() {
        assertThatThrownBy(() -> load(Map.of("browser", "firefox"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported browser 'firefox'. Supported: chromium, webkit");
    }

    @Test
    // @regression
    @DisplayName("Each browser prints as its lowercase name and names its one-time install command")
    void displayNameAndInstallCommand() {
        assertThat(BrowserChoice.WEBKIT).hasToString("webkit");
        assertThat(BrowserChoice.WEBKIT.installCommand())
                .isEqualTo("./mvnw -N exec:java -Dexec.args=\"install webkit\"");
    }
}
