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
 * Guards the boundary functional-spec.md section 2.2 draws between the screen checks and the
 * screen itself: a check names no on-screen element details and no raw web address, because
 * everything it touches goes through a Page Object. Plain-text scan, like the api-tests
 * boundary guard; it needs no lab or browser.
 */
class ScreenBoundaryGuardTest {

    // Written in pieces so this file does not contain the text it forbids (and so needs no
    // exemption from its own scan, or from the sibling guard that scans all of ui-tests/src for
    // a fixed-pause wait).
    private static final List<String> SCREEN_TOUCHING_DETAILS = List.of(
            "locator" + "(",
            "getByRole" + "(",
            "\"" + "#",
            "." + "navigate" + "(",
            "\"/" + "samples",
            "\"/" + "login",
            "com.microsoft.playwright." + "options");

    @Test
    @DisplayName("A screen check never calls a locator, a role query or navigate(), and never writes a raw address or a Playwright option type — only a Page Object touches the screen")
    void checksTalkToTheScreenOnlyThroughPageObjects() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String detail : SCREEN_TOUCHING_DETAILS) {
            offences.addAll(CheckSources.linesContaining(checkFiles, detail));
        }

        assertThat(offences)
                .as("checks must talk to the screen only through Page Objects; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
