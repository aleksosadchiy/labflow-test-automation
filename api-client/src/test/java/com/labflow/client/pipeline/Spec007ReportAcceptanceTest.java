// @layer: integration
// @spec: 007-allure-report
// @regression
package com.labflow.client.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature-level proof of the part of spec 007 (a visual Allure report of every check run) that
 * can be observed without a lab, a browser, a second Maven build or GitHub: what
 * {@code .github/scripts/report.sh}, the one documented way to build the report, does and refuses
 * to do. The script is copied into a temporary folder and run there with a stand-in {@code mvnw}
 * (the script uses paths relative to its working directory), so the repository's own
 * {@code target/} is never read or written and no report tool is downloaded.
 *
 * <h2>Criteria to tests (all 41 of functional-spec.md)</h2>
 * <p>Key: <b>[T]</b> automated here or in the named test, <b>[M]</b> manual / CI-level, evidence in
 * {@code tasks.md} (slice "Verify" task) and in the commit message of that slice.
 * No GitHub run id is recorded in tasks.md or in the commit messages, so none is quoted.
 *
 * <p><b>2.1 One report per run</b>
 * <ol>
 *   <li>[T] one command builds the report and says where it is:
 *       {@link #withResultsItBuildsThroughMavenAndSaysWhereTheReportIs}. The full build over real
 *       results: [M], Slice 1 and Slice 3 Verify (needs two Maven invocations, the second after
 *       the run).</li>
 *   <li>[M] only one family run, only that family reported: Slice 3 Verify ("only the workflow
 *       epic is present"); the shared folder emptied at the start of each root run is pinned by
 *       {@code LabRunWiringTest} and {@code UiRunWiringGuardTest} (results folder wiring).</li>
 *   <li>[M] opens by double-click, offline: Slice 1 Verify (one file, no {@code http(s)://}
 *       reference); the no-analytics tag is pinned by {@code CiWorkflowGuardTest} and here by
 *       {@link #aReportThatStillHoldsTheAnalyticsTagIsRefused}. A browser opening a file offline
 *       is not reasonable inside a module.</li>
 *   <li>[T] no results, no report, a clear message:
 *       {@link #withNoResultsFolderItRefusesAndBuildsNothing},
 *       {@link #withAnEmptyOrResultlessFolderItRefusesAndRemovesAStaleReport}.</li>
 *   <li>[T] running without asking for a report builds none: the report is built only by this
 *       script ({@code CiWorkflowGuardTest}, "build the report only through the script"); the
 *       unchanged run: [M], Slice 1 Verify ({@code -pl common,test-data,api-client test}).</li>
 *   <li>[M] a second run shows only the second run's results: Slice 1 Verify (count unchanged,
 *       not doubled); needs two real runs. The emptying execution is pinned by the wiring
 *       guards above.</li>
 * </ol>
 *
 * <p><b>2.2 Plain-language names</b>
 * <ol>
 *   <li>[M] first screen shows totals and duration: Allure's own overview page, seen in the
 *       Slice 1 and Slice 5 Verify runs.</li>
 *   <li>[M] each check under its plain-language name: since spec 008 the scenario's name, written
 *       by Allure's Cucumber adapter; the name rules are pinned by the feature-file guards.</li>
 *   <li>[M] verdict and labels: since spec 008 written by Allure's Cucumber adapter and the
 *       scenario hooks; the epic label: [M], Slice 2 and Slice 3 Verify.</li>
 *   <li>[M] filter by verdict: a feature of the Allure report page itself, not of this
 *       framework; seen in the Slice 3 Verify.</li>
 *   <li>[M] families told apart and viewable alone: epics "Workflow checks" / "Screen checks"
 *       on the base classes ({@code LabRunWiringTest}, {@code UiRunWiringGuardTest}); the report
 *       page's grouping: Slice 3 Verify.</li>
 *   <li>[T] only lab checks, no self-checks: since spec 008 only Allure's Cucumber adapter
 *       records, and the wiring guards switch Allure's own JUnit listener off; counted against
 *       the run: [M], Slice 1 Verify (48 results for 48 checks) and Slice 4 Verify
 *       (64 = 16 + 48).</li>
 * </ol>
 *
 * <p><b>2.3 A workflow check shows its requests</b>
 * <ol>
 *   <li>[T] requests in order as steps: {@code AllureStepAppenderTest}; against the run log in
 *       order: [M], Slice 2 Verify (209 steps across 48 checks).</li>
 *   <li>[T] a request line's kind, path, status, duration and identifiers: the line is the one
 *       {@code RequestLogFilter} writes (its own tests), forwarded by {@code AllureStepAppenderTest}.</li>
 *   <li>[M] a check with no request has no steps and no error: Slice 2 Verify.</li>
 * </ol>
 *
 * <p><b>2.4 A failed check explains itself</b>
 * <ol>
 *   <li>[M] failure message with expected and found: Slice 3 Verify (deliberately failed screen
 *       check) and Slice 5 Verify on GitHub; the message is Allure's, from the assertion.</li>
 *   <li>[M] picture and recording inside a failed screen check: Slice 3 Verify (two attachments,
 *       a PNG and a ZIP); needs a browser and a deliberate failure.</li>
 *   <li>[M] the recording steps through the check: Slice 3 Verify (trace opened the documented
 *       way).</li>
 *   <li>[M] a passing screen check has neither: Slice 3 Verify (15 of 16 results without
 *       attachments).</li>
 *   <li>[M] picture or recording not saved, the check still fails with its message: the helper
 *       logs and continues by design (technical-considerations 2.5); a throwaway-failure
 *       self-check (since removed with the extension) stayed green, Slice 3 Verify.</li>
 * </ol>
 *
 * <p><b>2.5 Nothing sensitive</b>
 * <ol>
 *   <li>[M] the access key is not found in any file of the report: Slice 4 Verify (scan of all
 *       161 result files and the decoded report, with {@code -DlogBodies=true}).</li>
 *   <li>[M] the sign-in password is not found: same scan.</li>
 *   <li>[T] identifiers only, no names or content in the requests: {@code AllureStepAppenderTest}
 *       (a body, logged at DEBUG, never becomes a step) and {@code NoAllureRequestCaptureGuardTest}
 *       (no module can depend on the raw request filter); scan: [M], Slice 4 Verify.</li>
 *   <li>[T] bodies switched on in the run log still stay out: {@code AllureStepAppenderTest}
 *       (DEBUG never forwarded); scan with {@code -DlogBodies=true}: [M], Slice 4 Verify.</li>
 *   <li>[M] a picture or recording shows only {@code TEST-} names: the lab checks only create such
 *       names ({@code NoHardcodedSettingsAcceptanceTest} and the data builders); Slice 4
 *       Verify scanned for patient names.</li>
 *   <li>[M] a failure message quotes only {@code TEST-} names: same reasoning; the message is the
 *       one the run summary shows (spec 006).</li>
 * </ol>
 *
 * <p><b>2.6 Kept with every automatic run</b> (all [M], GitHub: Slice 5 Verify, commit 7c03fd2 -
 * green run with one report of 64 checks, failed screen check, re-run, single family, broken build,
 * report that cannot be built)
 * <ol>
 *   <li>passed run keeps one combined report under a name with the browser: Slice 5 Verify;
 *       the name and the upload are pinned by {@code CiWorkflowGuardTest}.</li>
 *   <li>a failed check job still keeps the report with the failed check: Slice 5 Verify (probe branch).</li>
 *   <li>a hand-started single-family run reports that family only: Slice 5 Verify.</li>
 *   <li>a broken build skips Report and shows no failed reporting step: Slice 5 Verify; the
 *       {@code if} is pinned by {@code CiWorkflowGuardTest}.</li>
 *   <li>a downloaded report opens by double-click: Slice 5 Verify (download, unzip, open from disk).</li>
 *   <li>a re-run keeps a second report beside the first: Slice 5 Verify; {@code run_attempt} in
 *       the name is pinned by {@code CiWorkflowGuardTest}.</li>
 *   <li>the per-job summaries of spec 006 are still there: Slice 5 Verify; the summary steps are
 *       pinned by {@code CiWorkflowGuardTest}.</li>
 *   <li>the report is not published as a web page: no Pages step or write permission exists
 *       ({@code CiWorkflowGuardTest#permissionsAndNoConcurrency}).</li>
 * </ol>
 *
 * <p><b>2.7 Never changes a verdict</b>
 * <ol>
 *   <li>[M] green checks but no report: green run with a warning, Slice 5 Verify; pinned by
 *       {@code CiWorkflowGuardTest} (job-level {@code continue-on-error}, the warning step).</li>
 *   <li>[M] failed check and no report: the run's verdict still comes from the check (same
 *       job-level guard); Slice 5 Verify.</li>
 *   <li>[M] the report's count equals the run's own count, with the same verdicts: Slice 1,
 *       Slice 3 and Slice 4 Verify (64 = 16 + 48); the shared tag rule {@code Labels.isLabCheck}
 *       is tested by {@code LabelsTest} and used by {@code CheckCountListener}.</li>
 *   <li>[M] at most 10% slower: Slice 6 Verify (33.6 s against 33.0 s, 1.8%); a timing run.</li>
 * </ol>
 *
 * <p><b>2.8 Documented</b> (all [M]: README review, Slice 6 Verify "every path and command the
 * README mentions exists"; the command and its single-script rule are pinned by
 * {@code CiWorkflowGuardTest}.)
 * <ol>
 *   <li>the README names the command, the place and how to open it.</li>
 *   <li>the README's automatic-runs section names where the report is kept and how to open it.</li>
 *   <li>the README says what the report adds and what it never contains.</li>
 * </ol>
 */
class Spec007ReportAcceptanceTest {

    private static final Path REAL_SCRIPT = Path.of("..", ".github", "scripts", "report.sh");

    private static final String PAGE = "target/site/allure-maven-plugin/index.html";

    /** What the script printed and how it ended. */
    private record Outcome(int exit, String output) {
    }

    /** A made-up repository root holding a copy of the script and, optionally, a stand-in mvnw. */
    private static Path workspace(Path root, String standInMvnw) throws IOException {
        Path script = root.resolve(".github/scripts/report.sh");
        Files.createDirectories(script.getParent());
        Files.copy(REAL_SCRIPT, script);
        if (standInMvnw != null) {
            Path mvnw = root.resolve("mvnw");
            Files.writeString(mvnw, standInMvnw);
            assertThat(mvnw.toFile().setExecutable(true)).as("stand-in mvnw is executable").isTrue();
        }
        return root;
    }

    private static Outcome runScript(Path root) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("bash", ".github/scripts/report.sh")
                .directory(root.toFile())
                .redirectErrorStream(true);
        // The script must set the variable itself; an inherited value must not hide that.
        builder.environment().remove("ALLURE_NO_ANALYTICS");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("script finished in time").isTrue();
        return new Outcome(process.exitValue(), output);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    @DisplayName("With no results folder, building the report says there are no results to report, fails, and builds nothing")
    void withNoResultsFolderItRefusesAndBuildsNothing(@TempDir Path root) throws Exception {
        workspace(root, null);

        Outcome outcome = runScript(root);

        assertThat(outcome.exit()).as(outcome.output()).isNotZero();
        assertThat(outcome.output()).contains("No results to report");
        assertThat(root.resolve(PAGE)).as("no report page").doesNotExist();
        assertThat(root.resolve("target")).as("nothing created").doesNotExist();
    }

    @Test
    @DisplayName("With an empty results folder or one without a result file, the report is refused and an earlier run's report is removed, not left to be mistaken for this run's")
    void withAnEmptyOrResultlessFolderItRefusesAndRemovesAStaleReport(@TempDir Path root) throws Exception {
        workspace(root, null);
        Path results = root.resolve("target/allure-results");
        Files.createDirectories(results);
        write(root.resolve(PAGE), "<html>the report of an earlier run</html>");

        Outcome empty = runScript(root);

        assertThat(empty.exit()).as(empty.output()).isNotZero();
        assertThat(empty.output()).contains("No results to report").contains("Removed the report of an earlier run");
        assertThat(root.resolve(PAGE)).as("stale report removed").doesNotExist();

        write(results.resolve("environment.properties"), "browser=chromium\n");
        write(root.resolve(PAGE), "<html>the report of an earlier run</html>");

        Outcome withoutResultFile = runScript(root);

        assertThat(withoutResultFile.exit()).as(withoutResultFile.output()).isNotZero();
        assertThat(withoutResultFile.output()).contains("No results to report");
        assertThat(root.resolve(PAGE)).as("stale report removed").doesNotExist();
    }

    @Test
    @DisplayName("With results, the report is built through Maven with the tool's analytics switched off, and the output says where the report is and how many checks it covers")
    void withResultsItBuildsThroughMavenAndSaysWhereTheReportIs(@TempDir Path root) throws Exception {
        workspace(root, """
                #!/usr/bin/env bash
                echo "analytics=$ALLURE_NO_ANALYTICS" > mvnw-saw.txt
                echo "args=$*" >> mvnw-saw.txt
                mkdir -p target/site/allure-maven-plugin
                echo '<html>a report</html>' > target/site/allure-maven-plugin/index.html
                """);
        write(root.resolve("target/allure-results/a-result.json"), "{}");
        write(root.resolve("target/allure-results/b-result.json"), "{}");

        Outcome outcome = runScript(root);

        assertThat(outcome.exit()).as(outcome.output()).isZero();
        assertThat(outcome.output()).contains("Allure report: ").contains(PAGE).contains("(2 checks)");
        assertThat(Files.readString(root.resolve("mvnw-saw.txt")))
                .contains("analytics=true")
                .contains("allure:report");
    }

    @Test
    @DisplayName("A built report that still holds the analytics tag is refused, so a page that phones home is never handed out")
    void aReportThatStillHoldsTheAnalyticsTagIsRefused(@TempDir Path root) throws Exception {
        workspace(root, """
                #!/usr/bin/env bash
                mkdir -p target/site/allure-maven-plugin
                echo '<script src="https://www.googletagmanager.com/gtag/js"></script>' > target/site/allure-maven-plugin/index.html
                """);
        write(root.resolve("target/allure-results/a-result.json"), "{}");

        Outcome outcome = runScript(root);

        assertThat(outcome.exit()).as(outcome.output()).isNotZero();
        assertThat(outcome.output()).contains("not safe to share");
        assertThat(outcome.output()).doesNotContain("Allure report: ");
    }
}
