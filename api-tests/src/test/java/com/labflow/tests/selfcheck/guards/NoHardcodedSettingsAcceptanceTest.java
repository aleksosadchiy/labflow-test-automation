// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.tests.selfcheck.guards;

import com.labflow.tests.selfcheck.guards.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance check for functional-spec.md section 2.5 (last criterion): no check or helper of
 * this module writes the lab address, the access key or a browser name; each comes only from
 * the settings. A plain-text scan, like the other guards; needs no lab. The literals are written
 * in pieces so this file does not contain what it forbids.
 */
class NoHardcodedSettingsAcceptanceTest {

    private static final List<String> FORBIDDEN = List.of(
            "localhost" + ":8080",
            "mock-lab-local-" + "token",
            "\"chrom" + "ium\"",
            "\"web" + "kit\"");

    @Test
    @DisplayName("No check or helper writes a lab address, access key or browser name; the settings are the only source")
    void settingsComeOnlyFromTheSettings() {
        List<Path> files = new ArrayList<>(CheckSources.checkFiles());
        files.addAll(CheckSources.javaFilesUnder(Path.of("src", "test", "java", "com", "labflow", "tests", "framework")));
        files.addAll(CheckSources.javaFilesUnder(Path.of("..", "api-client", "src", "main")));

        List<String> offences = new ArrayList<>();
        for (String forbidden : FORBIDDEN) {
            offences.addAll(CheckSources.linesContaining(files, forbidden));
        }

        assertThat(offences)
                .as("settings must come from the configuration, not from the code; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
