// @layer: unit
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.guards;

import com.labflow.ui.tests.selfcheck.guards.support.DryRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A dry run of the screen feature files (functional-spec.md section 2.5, last paragraph; technical
 * considerations 2.8): every Gherkin line is matched against the step definitions without being
 * carried out. A line no step definition matches fails here, naming the line and its file, and a
 * step definition no line uses fails here too, so neither can pile up unnoticed. No lab is needed.
 * The two tests at the end prove the guard itself: a deliberate unknown line is reported with its
 * file and line number, and an unused definition is told apart from a used one.
 */
class DryRunGuardTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("Every Gherkin line of the screen feature files has exactly one step definition, so no line can be skipped as undefined")
    void everyLineHasAStepDefinition() {
        DryRun.Result result = DryRun.ofTheFeatures();

        assertThat(result.entries()).as("scenario entries the dry run saw (a guard that saw none proves nothing)").isNotEmpty();
        assertThat(result.problems())
                .as("lines with no step definition (UNDEFINED) or with two (AMBIGUOUS), by file and line")
                .isEmpty();
        assertThat(result.engineFailures()).as("tests the engine reported as failed during the dry run").isEmpty();
    }

    @Test
    @DisplayName("Every step definition is used by at least one Gherkin line, so dead step code is removed with the scenario that needed it")
    void everyStepDefinitionIsUsed() {
        DryRun.Result result = DryRun.ofTheFeatures();

        assertThat(result.definitions()).as("step definitions the engine registered (a guard that saw none proves nothing)").isNotEmpty();
        assertThat(result.unused())
                .as("step definitions that no line of any feature file matches; delete them or write the scenario that uses them")
                .isEmpty();
    }

    @Test
    @DisplayName("A line nothing can carry out is reported with its file and line number, as the failure of a run must name them")
    void anUnknownLineIsReportedWithItsFileAndLine() throws IOException {
        Path feature = temp.resolve("deliberate-unknown-line.feature");
        Files.writeString(feature, String.join("\n",
                "Feature: A deliberate unknown line",
                "",
                "  Scenario: Nothing defines the second line",
                "    Given the lab holds a registered patient",
                "    When somebody does what no step definition describes",
                ""));

        DryRun.Result result = DryRun.of(DiscoverySelectors.selectFile(feature.toString()));

        assertThat(result.problems().stream().map(DryRun.Problem::toString).toList())
                .as("the unknown line is named with its file, line number and text; the known line is not named")
                .containsExactly("UNDEFINED: deliberate-unknown-line.feature:5 'When somebody does what no step definition describes'");
    }

    @Test
    @DisplayName("A step definition that no line of the run matches is listed as unused, and the one that was matched is not")
    void anUnusedDefinitionIsToldApartFromAUsedOne() throws IOException {
        Path feature = temp.resolve("one-line.feature");
        Files.writeString(feature, String.join("\n",
                "Feature: One known line",
                "",
                "  Scenario: Only one step is used",
                "    Given the lab holds a registered patient",
                ""));

        DryRun.Result result = DryRun.of(DiscoverySelectors.selectFile(feature.toString()));

        List<String> unused = result.unused();
        assertThat(result.problems()).as("the one line is defined").isEmpty();
        assertThat(result.used()).as("definitions this one line matched").hasSize(1);
        assertThat(unused).as("every other definition is unused in this tiny run").hasSize(result.definitions().size() - 1);
        assertThat(unused).as("the matched definition is not listed as unused").doesNotContainAnyElementsOf(result.used());
    }
}
