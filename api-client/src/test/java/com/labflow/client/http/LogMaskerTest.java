package com.labflow.client.http;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the bodies written to the run log hide patient names and passwords
 * (functional-spec.md §2.6, technical-considerations.md §2.8): {@code name} and
 * {@code password} values are replaced at any depth, everything else is kept, and a body
 * that is not JSON is never shown at all.
 *
 * @spec: 005-reliable-isolated-execution
 */
class LogMaskerTest {

    @Test
    // @regression
    @DisplayName("A top-level name is replaced with *** and the other fields are kept as they were")
    void topLevelNameIsMasked() {
        String masked = LogMasker.mask("{\"id\":\"P-1\",\"name\":\"TEST-patient-Ada\",\"age\":42}");

        assertThat(masked).isEqualTo("{\"id\":\"P-1\",\"name\":\"***\",\"age\":42}");
    }

    @Test
    // @regression
    @DisplayName("Names and passwords nested in objects and arrays are all replaced")
    void nestedNamesAndPasswordsAreMasked() {
        String body = "{\"order\":{\"patient\":{\"name\":\"TEST-patient-Bob\"}},"
                + "\"login\":{\"username\":\"admin\",\"password\":\"I_Like_This_Lab\"},"
                + "\"patients\":[{\"id\":\"P-1\",\"name\":\"TEST-patient-Cy\"},{\"id\":\"P-2\",\"Name\":\"TEST-patient-Di\"}]}";

        String masked = LogMasker.mask(body);

        assertThat(masked)
                .doesNotContain("TEST-patient-").doesNotContain("I_Like_This_Lab")
                .contains("\"username\":\"admin\"").contains("\"id\":\"P-1\"").contains("\"id\":\"P-2\"")
                .contains("{\"patient\":{\"name\":\"***\"}}").contains("\"password\":\"***\"")
                .contains("\"Name\":\"***\"");
    }

    @Test
    // @regression
    @DisplayName("A name that is not text, such as an object, is replaced as a whole")
    void nonTextNameIsMaskedWhole() {
        assertThat(LogMasker.mask("{\"name\":{\"first\":\"Ada\",\"last\":\"Lovelace\"}}"))
                .isEqualTo("{\"name\":\"***\"}");
    }

    @Test
    // @regression
    @DisplayName("A bare JSON array of patients is masked element by element")
    void bareArrayIsMasked() {
        assertThat(LogMasker.mask("[{\"id\":\"P-1\",\"name\":\"Ada\"},{\"id\":\"P-2\",\"name\":\"Bob\"}]"))
                .isEqualTo("[{\"id\":\"P-1\",\"name\":\"***\"},{\"id\":\"P-2\",\"name\":\"***\"}]");
    }

    @Test
    // @regression
    @DisplayName("A body without names or passwords comes back unchanged, in compact form")
    void bodyWithoutSecretsIsUnchanged() {
        assertThat(LogMasker.mask("{ \"patientId\" : \"P-1\", \"sampleType\" : \"BLOOD\" }"))
                .isEqualTo("{\"patientId\":\"P-1\",\"sampleType\":\"BLOOD\"}");
    }

    @Test
    // @regression
    @DisplayName("A body that is not JSON is shown only as its length, never its text")
    void nonJsonBodyIsShownAsItsLength() {
        String malformed = "{\"name\":\"TEST-patient-Eve\"";

        assertThat(LogMasker.mask(malformed)).isEqualTo("<non-JSON body, " + malformed.length() + " chars>");
        assertThat(LogMasker.mask("plain text")).isEqualTo("<non-JSON body, 10 chars>");
    }

    @Test
    // @regression
    @DisplayName("JSON followed by extra text counts as not JSON, so the extra text is never shown")
    void trailingTextMakesItNonJson() {
        String body = "{\"id\":\"P-1\"} TEST-patient-Fay";

        assertThat(LogMasker.mask(body)).isEqualTo("<non-JSON body, " + body.length() + " chars>");
    }

    @Test
    // @regression
    @DisplayName("An empty, blank or missing body is logged as <empty body> and never throws")
    void emptyOrMissingBodyIsSafe() {
        assertThat(LogMasker.mask("")).isEqualTo("<empty body>");
        assertThat(LogMasker.mask("   ")).isEqualTo("<empty body>");
        assertThat(LogMasker.mask(null)).isEqualTo("<empty body>");
        assertThatCode(() -> LogMasker.mask("\u0000{[")).doesNotThrowAnyException();
    }
}
