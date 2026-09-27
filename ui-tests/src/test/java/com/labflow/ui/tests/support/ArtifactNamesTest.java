package com.labflow.ui.tests.support;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArtifactNames} (tech spec §2.5, FS 2.9 ¶2–3): the folder-name sanitiser
 * used by {@code BrowserExtension} to name a failed check's artifact folder.
 *
 * @spec: 004-ui-automation-page-objects
 */
class ArtifactNamesTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    // @regression
    @CsvSource({
            "A Registered Sample Appears In The List, a-registered-sample-appears-in-the-list",
            "Already-Lower-Case, already-lower-case",
            "'  leading and trailing spaces  ', leading-and-trailing-spaces",
            "'punctuation!! galore??', punctuation-galore",
            "'multiple___underscores', multiple-underscores",
    })
    @DisplayName("Lower-cases the name and collapses non-alphanumeric runs into a single dash, trimmed")
    void sanitisesToADashedLowerCaseName(String raw, String expected) {
        assertThat(ArtifactNames.sanitise(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] blank input: \"{0}\"")
    // @regression
    @CsvSource({
            "''",
            "'   '",
            "'!!!___???'",
    })
    @DisplayName("Falls back to 'unnamed' when nothing usable is left")
    void fallsBackToUnnamedForAnEmptyResult(String raw) {
        assertThat(ArtifactNames.sanitise(raw)).isEqualTo("unnamed");
    }

    @Test
    // @regression
    @DisplayName("Falls back to 'unnamed' for a null name")
    void fallsBackToUnnamedForNull() {
        assertThat(ArtifactNames.sanitise(null)).isEqualTo("unnamed");
    }

    @Test
    // @regression
    @DisplayName("Truncates a long name to a sensible length, with no trailing dash left by the cut")
    void truncatesLongNames() {
        String longName = "word-".repeat(30); // 150 chars, every 5th char a dash

        String sanitised = ArtifactNames.sanitise(longName);

        assertThat(sanitised).hasSizeLessThanOrEqualTo(80);
        assertThat(sanitised).doesNotEndWith("-");
    }

    /**
     * FS 2.9 ¶5: the password must never appear in a saved artifact's name. This display name is
     * what a parameterized test's per-invocation display name would look like if someone wrote a
     * {@code name=} pattern that echoes an argument next to the scenario name, and that argument
     * happened to be the real password (a realistic mistake: {@code @ParameterizedTest(name =
     * "Sign in [{0}]")} over a list of passwords). {@code BrowserExtension} is not supposed to
     * ever pass such a string in (it uses the argument-free template-level display name instead,
     * see the class Javadoc), but the sanitiser itself must not leak the value even so.
     */
    @Test
    // @regression
    @DisplayName("A password sitting in a bracketed parameter part never reaches the sanitised name")
    void neverLeaksAPasswordFromTheParameterPart() {
        String password = "I_Like_This_Lab";
        String displayNameWithPassword = "Sign in [" + password + "]";

        String sanitised = ArtifactNames.sanitise(displayNameWithPassword);

        assertThat(sanitised)
                .as("the sanitised folder name for '%s'", displayNameWithPassword)
                .isEqualTo("sign-in")
                .doesNotContainIgnoringCase(password);
    }
}
