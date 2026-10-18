// @layer: integration
// @spec: 008-report-navigation
// @regression
package com.labflow.client.pipeline;

import com.labflow.client.support.ScenarioFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Functional-spec 008 section 2.2, last criterion, and section 2.6 ("the name shown in the run-page
 * summary is a name the reader can find in that run's report by typing it into the search box"):
 * {@code .github/scripts/junit_summary.py}, which writes the run page's summary, is run on a
 * Surefire XML shaped like the one the Cucumber suite writes (one test class for every scenario,
 * the scenario's short name as the test case's name). A failed scenario must appear under exactly
 * its short name, with the first line of its message, and without a class column that would repeat
 * one name on every row.
 */
class Spec008RunPageSummaryAcceptanceTest {

    private static final Path SCRIPT = Path.of("..", ".github", "scripts", "junit_summary.py");

    private static String run(Path reports) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("python3", "-I", SCRIPT.toAbsolutePath().toString(), "API tests", reports.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("script finished").isTrue();
        assertThat(process.exitValue()).as("the summary never fails the job").isZero();
        return output;
    }

    private static String xml(String shortName, String failureMessage) {
        String body = failureMessage == null ? "" : "<failure message=\"" + failureMessage + "\" type=\"org.opentest4j.AssertionFailedError\">trace</failure>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite name=\"com.labflow.tests.WorkflowScenariosTest\" time=\"3\" tests=\"2\" errors=\"0\" skipped=\"0\" failures=\"1\">\n"
                + "<testcase name=\"A new sample starts at Accessioned\" classname=\"WorkflowScenariosTest\" time=\"1\"/>\n"
                + "<testcase name=\"" + shortName + "\" classname=\"WorkflowScenariosTest\" time=\"1\">" + body + "</testcase>\n"
                + "</testsuite>\n";
    }

    @Test
    @DisplayName("A failed scenario is listed in the run-page summary under exactly its short name from the scenario file, with its message and no class column")
    void aFailedScenarioIsListedUnderItsShortName(@TempDir Path reports) throws Exception {
        assumeTrue(new ProcessBuilder("python3", "--version").start().waitFor() == 0, "python3 is not installed");
        String shortName = ScenarioFiles.in(ScenarioFiles.WORKFLOW, "API tests").stream()
                .map(ScenarioFiles.Entry::name).filter(n -> n.contains("without a patient")).findFirst().orElseThrow();
        Files.writeString(reports.resolve("TEST-com.labflow.tests.WorkflowScenariosTest.xml"), xml(shortName, "expected: 400 but was: 201"));

        String summary = run(reports);

        assertThat(summary).contains("**API tests**: 1 passed, 1 failed");
        assertThat(List.of(summary.split("\n"))).as("the failed scenario's row: its short name, then its message")
                .contains("| " + shortName + " | expected: 400 but was: 201 |");
        assertThat(summary).as("no class column").contains("| Check | Message |").doesNotContain("WorkflowScenariosTest");
    }

    @Test
    @DisplayName("When every scenario passes the summary lists no names, and when there is no report it says the lab did not start")
    void passingAndMissingReportsAreSummarisedPlainly(@TempDir Path reports) throws Exception {
        assumeTrue(new ProcessBuilder("python3", "--version").start().waitFor() == 0, "python3 is not installed");
        Path empty = Files.createDirectory(reports.resolve("empty"));
        Path green = Files.createDirectory(reports.resolve("green"));
        Files.writeString(green.resolve("TEST-x.xml"), xml("Anything", null).replace("failures=\"1\"", "failures=\"0\""));

        assertThat(run(green)).contains("2 passed").doesNotContain("| Check |");
        assertThat(run(empty)).contains("no results");
    }
}
