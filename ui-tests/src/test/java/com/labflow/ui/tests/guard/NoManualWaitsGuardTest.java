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
 * Guards the anti-flakiness rule (technical-considerations.md section 2.6): nothing in
 * {@code ui-tests} waits by pausing for a fixed number of milliseconds - neither the JDK's own
 * blocking sleep nor Playwright's own fixed-delay wait, which the Playwright docs themselves
 * discourage outside debugging. Playwright's auto-waiting, or an explicit condition wait, always
 * replaces it. Scans the whole module's sources, main and test alike, so a fixed pause could not
 * slip in anywhere - including this guard's own sibling files - which is also why this file names
 * neither banned call outright. Plain-text scan; needs no lab or browser.
 */
class NoManualWaitsGuardTest {

    // Written in pieces so this file - and every other file this guard scans - can discuss the
    // rule without being flagged by it.
    private static final List<String> FIXED_PAUSES = List.of(
            "Thread." + "sleep",
            "wait" + "ForTimeout");

    private static final Path MODULE_SOURCES_ROOT = Path.of("src");

    @Test
    @DisplayName("Nothing in ui-tests pauses for a fixed number of milliseconds anywhere in the module - Playwright's own waiting is used instead")
    void nothingWaitsWithAFixedPause() {
        List<Path> sources = CheckSources.javaFilesUnder(MODULE_SOURCES_ROOT);

        List<String> offences = new ArrayList<>();
        for (String pause : FIXED_PAUSES) {
            offences.addAll(CheckSources.linesContaining(sources, pause));
        }

        assertThat(offences)
                .as("nothing may wait with a fixed pause; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
