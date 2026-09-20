// @layer: unit
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests;

import com.labflow.tests.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards two boundaries of the framework that no other check would notice being crossed:
 * the workflow checks stay free of low-level web-request details (functional-spec.md section
 * 2.2, in addition to the existing ban on the request library itself), and nothing in the
 * framework builds, starts or stops the lab (section 2.1). Plain-text scans, like the
 * request-library guard; they need no lab.
 */
class FrameworkBoundaryGuardTest {

    // Written in pieces so this file does not contain the text it forbids (and so needs no exemption).
    private static final List<String> LOW_LEVEL_WEB_DETAILS = List.of(
            "java." + "net",             // the JDK's own web-request classes
            "Http" + "Client",
            "Http" + "URLConnection",
            "\"/" + "api");              // a URL path written out inside a check
    private static final List<String> PROCESS_STARTING = List.of(
            "Process" + "Builder",
            "Runtime" + ".getRuntime");

    // Maven runs the checks with the module directory (api-tests) as the working directory.
    private static final List<Path> FRAMEWORK_SOURCES = List.of(
            Path.of("..", "common", "src"),
            Path.of("..", "api-client", "src"),
            Path.of("src"));

    @Test
    @DisplayName("The workflow checks show no low-level web-request details: no JDK web classes and no URL paths, only business steps")
    void checksShowNoLowLevelWebRequestDetails() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String detail : LOW_LEVEL_WEB_DETAILS) {
            offences.addAll(CheckSources.linesContaining(checkFiles, detail));
        }

        assertThat(offences)
                .as("checks must talk to the lab only through the lab layer; found: %s", String.join(" | ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("Nothing in the framework starts an operating-system process, so the lab is never built, started or stopped by a run")
    void frameworkNeverStartsOrStopsTheLab() {
        List<Path> sources = new ArrayList<>();
        for (Path folder : FRAMEWORK_SOURCES) {
            sources.addAll(CheckSources.javaFilesUnder(folder));
        }

        List<String> offences = new ArrayList<>();
        for (String call : PROCESS_STARTING) {
            offences.addAll(CheckSources.linesContaining(sources, call));
        }

        assertThat(offences)
                .as("the framework must leave the lab to the engineer; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
