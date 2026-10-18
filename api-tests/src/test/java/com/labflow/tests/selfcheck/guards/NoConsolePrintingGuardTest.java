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
 * Guards functional-spec.md section 2.6 ("a reader searching the framework for direct printing
 * to the console finds none; everything goes through the run log"). A plain-text scan of every
 * framework module's sources, main and test, like {@code FrameworkBoundaryGuardTest}; the lab
 * itself ({@code mock-lab-app}) is not part of the framework and is not scanned. Needs no lab.
 */
class NoConsolePrintingGuardTest {

    // Written in pieces so this file does not contain the text it forbids (and so needs no exemption).
    private static final List<String> CONSOLE_PRINTING = List.of(
            "System." + "out",
            "System." + "err",
            "print" + "StackTrace");

    // Maven runs the checks with the module directory (api-tests) as the working directory.
    private static final List<Path> FRAMEWORK_SOURCES = List.of(
            Path.of("..", "common", "src"),
            Path.of("..", "api-client", "src"),
            Path.of("..", "test-data", "src"),
            Path.of("src"),
            Path.of("..", "ui-tests", "src"));

    @Test
    @DisplayName("Nothing in the framework prints to the console directly, so everything an engineer reads comes through the run log")
    void frameworkNeverPrintsToTheConsoleDirectly() {
        List<Path> sources = new ArrayList<>();
        for (Path folder : FRAMEWORK_SOURCES) {
            sources.addAll(CheckSources.javaFilesUnder(folder));
        }

        List<String> offences = new ArrayList<>();
        for (String call : CONSOLE_PRINTING) {
            offences.addAll(CheckSources.linesContaining(sources, call));
        }

        assertThat(offences)
                .as("the framework must write to the run log, not the console; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
