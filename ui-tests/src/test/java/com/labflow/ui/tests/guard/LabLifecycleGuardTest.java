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
 * Guards FS 2.1 ¶6 ("the lab itself is not built, started or stopped by [the screen checks]"),
 * mirroring {@code api-tests}' {@code FrameworkBoundaryGuardTest#frameworkNeverStartsOrStopsTheLab}
 * - which scans {@code common}, {@code api-client} and its own {@code api-tests} sources, but
 * never {@code ui-tests}. That left this criterion, for the screen checks specifically, with no
 * automated evidence at all: only the Verify conventions' own "never touch anything listening on
 * 8080" discipline, applied by a human running the Verify steps. Plain-text scan over the whole
 * module (main and test alike, like the sibling {@code NoManualWaitsGuardTest}); needs no lab or
 * browser.
 */
class LabLifecycleGuardTest {

    // Written in pieces, matching the api-tests original, so this file needs no exemption from
    // its own scan.
    private static final List<String> PROCESS_STARTING = List.of(
            "Process" + "Builder",
            "Runtime" + ".getRuntime");

    private static final Path MODULE_SOURCES_ROOT = Path.of("src");

    @Test
    @DisplayName("Nothing in ui-tests starts an operating-system process, so the lab is never built, started or stopped by a screen-check run")
    void screenChecksNeverStartOrStopTheLab() {
        List<Path> sources = CheckSources.javaFilesUnder(MODULE_SOURCES_ROOT);

        List<String> offences = new ArrayList<>();
        for (String call : PROCESS_STARTING) {
            offences.addAll(CheckSources.linesContaining(sources, call));
        }

        assertThat(offences)
                .as("the screen checks must leave the lab to the engineer; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
