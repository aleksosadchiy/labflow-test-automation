// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.guard;

import com.labflow.ui.tests.guard.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the other half of the same boundary (functional-spec.md section 2.2): a Page Object
 * describes only what a person can see or do on a screen and carries no pass/fail verdict.
 * Verdicts live only in the checks. Plain-text scan; needs no lab or browser.
 */
class PageObjectVerdictGuardTest {

    // Written in pieces so this file does not contain the text it forbids.
    private static final List<String> VERDICT_KEYWORDS = List.of(
            "assert" + "That",
            "Assert" + "ions",            // also catches PlaywrightAssertions, a substring match
            "Playwright" + "Assertions");

    private static final Path PAGE_OBJECTS_ROOT =
            Path.of("src", "main", "java", "com", "labflow", "ui", "pages");

    @Test
    @DisplayName("A Page Object carries no assertion — assertThat, Assertions or PlaywrightAssertions all stay out of ui/pages")
    void pageObjectsCarryNoVerdict() {
        List<Path> pageObjectFiles = CheckSources.javaFilesUnder(PAGE_OBJECTS_ROOT);

        List<String> offences = new ArrayList<>();
        for (String keyword : VERDICT_KEYWORDS) {
            offences.addAll(CheckSources.linesContaining(pageObjectFiles, keyword));
        }

        assertThat(offences)
                .as("Page Objects must carry no verdict; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
