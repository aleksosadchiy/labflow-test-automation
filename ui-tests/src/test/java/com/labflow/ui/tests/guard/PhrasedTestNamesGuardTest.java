// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards FS 2.3 ¶3 ("When the engineer lists the screen checks in the run output, then each
 * appears under its plain-language name"): the root {@code pom.xml}'s Surefire
 * {@code pluginManagement} must keep configuring the phrased JUnit 5 reporters (added during the
 * Slice 2 Verify, after the first attempt found business names never reaching the run output).
 * Nothing had proven that wiring stays in place since - the Slice 2 Verify quoted one XML report
 * and moved on. A plain-text read of the root POM, like the guard tests that scan check sources;
 * needs no lab or browser.
 *
 * <p>This does not re-parse the POM's XML structure (that would only prove the reporter elements
 * exist somewhere in the file, not that Surefire ever sees them); it checks the same four
 * settings the technical considerations document by name, and that they are configured as
 * {@code true} rather than merely present and disabled.
 */
class PhrasedTestNamesGuardTest {

    private static final Path ROOT_POM = Path.of("..", "pom.xml");

    @Test
    @DisplayName("The root POM still configures Surefire's phrased-name reporters, so a check's business name reaches the console and the XML/TXT reports")
    void rootPomStillConfiguresPhrasedReporters() {
        String pom = readRootPom();

        assertThat(pom)
                .as("the statelessTestsetReporter implementation naming the phrased-name JUnit 5 reporter")
                .contains("JUnit5Xml30StatelessReporter");
        assertThat(pom)
                .as("the statelessTestsetInfoReporter implementation naming the phrased-name JUnit 5 reporter")
                .contains("JUnit5StatelessTestsetInfoReporter");
        assertThat(pom)
                .as("the setting that puts a check's @DisplayName / parameterized row name into the TEST-*.xml testcase entries")
                .contains("<usePhrasedTestCaseMethodName>true</usePhrasedTestCaseMethodName>");
        assertThat(pom)
                .as("the setting that puts a check class's phrased name into the console's Running/Tests-run lines")
                .contains("<usePhrasedClassNameInRunning>true</usePhrasedClassNameInRunning>");
        assertThat(pom)
                .as("the setting that puts a check class's phrased name into the console's per-class summary")
                .contains("<usePhrasedClassNameInTestCaseSummary>true</usePhrasedClassNameInTestCaseSummary>");
    }

    private static String readRootPom() {
        if (!Files.isRegularFile(ROOT_POM)) {
            throw new IllegalStateException("Expected the root POM at " + ROOT_POM.toAbsolutePath()
                    + " - Maven runs the checks with the module directory as the working directory");
        }
        try {
            return Files.readString(ROOT_POM);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + ROOT_POM, e);
        }
    }
}
