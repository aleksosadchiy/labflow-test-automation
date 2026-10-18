// @layer: unit
// @spec: 006-ci-cd-pipeline, 007-allure-report, 008-report-navigation
// @regression
package com.labflow.client.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A framework self-check about the build pipeline (spec 006, and the Report job of spec 007): pins the decisions in
 * {@code .github/workflows/ci.yml} (and the access-key script it calls) that cannot be exercised without a real GitHub run, so a
 * careless edit shows up in the lab-free Build job instead of as a silently weaker pipeline.
 * Reads the file as plain text (no YAML library, like the other guard tests); lives in
 * api-client because that module's tests run in the Build job and already have AssertJ.
 * Run-level behaviour (green runs, re-run, skipped jobs, artifacts) was proven on GitHub in the
 * slice Verify tasks and is not re-tested here.
 */
class CiWorkflowGuardTest {

    private static final Path CI = Path.of("..", ".github", "workflows", "ci.yml");
    private static final Path ACCESS_KEY_SCRIPT = Path.of("..", ".github", "scripts", "access_key.sh");
    private static final Path REPORT_SCRIPT = Path.of("..", ".github", "scripts", "report.sh");
    private static final Path README = Path.of("..", "README.md");
    private static final String YAML = readFile(CI);

    @Test
    @DisplayName("The workflow starts on every push, on pull requests into main, and by hand with a family and a browser choice (FS 2.1)")
    void triggers() {
        String on = section(YAML, "on:", "\n# Least privilege");
        assertThat(on).as("push trigger").containsPattern("(?m)^  push:");
        assertThat(on).as("pull_request into main").containsPattern("(?s)  pull_request:\\s+(#[^\\n]*\\s+)*branches: \\[main\\]");
        assertThat(on).as("manual trigger").containsPattern("(?m)^  workflow_dispatch:");
        assertThat(on).as("family choice").containsPattern("options: \\[both, api, ui\\]");
        assertThat(on).as("browser choice").containsPattern("options: \\[chromium, webkit\\]");
    }

    @Test
    @DisplayName("Quick successive pushes both complete: no concurrency block; least privilege: contents read only (FS 2.1, security)")
    void permissionsAndNoConcurrency() {
        assertThat(code(YAML)).as("no concurrency block (both runs must finish)").doesNotContain("concurrency:");
        assertThat(YAML).as("least-privilege permissions").containsPattern("(?m)^permissions:\\n  contents: read\\n");
        assertThat(code(YAML)).as("no write permission anywhere").doesNotContain(": write");
    }

    @Test
    @DisplayName("There are exactly four jobs named Build, API tests, UI tests and Report, the two check jobs gated on Build (FS 2.2, spec 007 FS 2.6; the check jobs were named Workflow checks / Screen checks until 2026-10-18)")
    void jobsAndGate() {
        assertThat(job("build")).contains("name: Build");
        assertThat(job("api-tests")).contains("\n    name: API tests\n").containsPattern("(?m)^    needs: build$");
        assertThat(job("ui-tests")).contains("\n    name: UI tests\n").containsPattern("(?m)^    needs: build$");
        assertThat(job("report")).contains("name: Report");
        assertThat(job("build")).as("Build is the root of the graph").doesNotContain("needs:");
        assertThat(Pattern.compile("(?m)^  [a-z-]+:$").matcher(YAML.substring(YAML.indexOf("\njobs:")))
                .results().count()).as("number of jobs").isEqualTo(4);
    }

    @Test
    @DisplayName("The run page's summaries are titled with the families' plain names, API tests and UI tests (browser in brackets), not the job names (spec 008 Change Log 2026-10-18)")
    void summaryTitlesUseThePlainFamilyNames() {
        assertThat(job("api-tests")).contains("junit_summary.py \"API tests\" api-tests/target/surefire-reports");
        assertThat(job("ui-tests")).contains("junit_summary.py \"UI tests ($BROWSER)\" ui-tests/target/surefire-reports");
    }

    @Test
    @DisplayName("Every job has a 15-minute timeout and a pinned runner image (FS 2.6)")
    void timeoutAndRunner() {
        for (String id : new String[]{"build", "api-tests", "ui-tests", "report"}) {
            assertThat(job(id)).as("job " + id)
                    .contains("timeout-minutes: 15")
                    .containsPattern("(?m)^    runs-on: ubuntu-24\\.04$");
        }
        assertThat(code(YAML)).as("no floating runner image").doesNotContain("ubuntu-latest");
    }

    @Test
    @DisplayName("Both check jobs run the regression set plus the untagged self-checks, never the whole suite (FS 2.1)")
    void tagFilter() {
        assertThat(code(job("api-tests"))).contains("-pl api-tests -am test -Dgroups=\"regression | !(api | ui)\"");
        assertThat(code(job("ui-tests"))).contains("-pl ui-tests -am test -Dgroups=\"regression | !(api | ui)\"");
    }

    @Test
    @DisplayName("Results are summarised, the lab is stopped and the reports are kept even when checks fail; failure pictures only on failure (FS 2.3, 2.4, 2.5)")
    void alwaysAndFailureSteps() {
        for (String id : new String[]{"build", "api-tests", "ui-tests"}) {
            assertThat(step(job(id), "Summarise results")).as(id + " summary step").contains("if: always()");
        }
        for (String id : new String[]{"api-tests", "ui-tests"}) {
            assertThat(step(job(id), "Stop the Mock Lab")).as(id + " lab stop").contains("if: always()");
            assertThat(step(job(id), "Keep the results")).as(id + " kept results").contains("if: always()");
        }
        assertThat(step(job("ui-tests"), "Keep the failure pictures and recordings"))
                .as("failure artifact").contains("if: failure()").doesNotContain("always()");
        assertThat(code(YAML)).as("90-day default retention is the decision").doesNotContain("retention-days");
    }

    @Test
    @DisplayName("The access key is never a literal in the workflow, is not job-level env, and is masked before it reaches the environment (FS 2.3, 2.5)")
    void accessKeyHandling() {
        assertThat(YAML).as("the lab's local key literal").doesNotContain("mock-lab-local-token");
        for (String id : new String[]{"api-tests", "ui-tests"}) {
            String job = job(id);
            String env = job.contains("\n    env:") ? section(job, "\n    env:", "\n    steps:") : "";
            assertThat(code(env)).as(id + " job-level env").doesNotContain("API_TOKEN");
            assertThat(step(job, "Choose the access key")).as(id + " uses the one shared script")
                    .contains("run: .github/scripts/access_key.sh");
        }
        String script = readFile(ACCESS_KEY_SCRIPT);
        assertThat(script).as("the script's local key literal").doesNotContain("mock-lab-local-token");
        assertThat(script).as("masks before exporting")
                .contains("::add-mask::$KEY").contains("API_TOKEN=$KEY\" >> \"$GITHUB_ENV\"");
        assertThat(script.indexOf("echo \"::add-mask::")).isLessThan(script.indexOf("echo \"API_TOKEN="));
    }

    @Test
    @DisplayName("Build also packages the Mock Lab, so the lab's dependencies are in the Maven cache the check jobs restore (FS 2.6)")
    void buildWarmsTheLabCache() {
        assertThat(code(job("build"))).contains("cd mock-lab-app && ./mvnw -B -ntp -q package -DskipTests");
        assertThat(code(job("build"))).as("Build also fetches the Playwright CLI, so 'Install the browser' does not depend on Maven Central")
                .contains("./mvnw -B -ntp -N exec:java -Dexec.args=\"--version\"");
        assertThat(code(job("build"))).contains("cache: maven");
    }

    @Test
    @DisplayName("Build checks the formatting of the framework and of the Mock Lab before it compiles anything")
    void buildChecksTheFormatting() {
        String check = step(job("build"), "Check the formatting");
        assertThat(check).contains("./mvnw -B -ntp spotless:check")
                .contains("cd mock-lab-app && ./mvnw -B -ntp spotless:check");
        assertThat(job("build").indexOf("- name: Check the formatting"))
                .isLessThan(job("build").indexOf("- name: Compile everything"));
    }

    @Test
    @DisplayName("Build scans the whole git history for secrets with a pinned, checksum-verified scanner")
    void buildScansForSecrets() {
        String build = job("build");
        assertThat(step(build, "Check out the code")).as("whole history, so removed secrets are seen too")
                .contains("fetch-depth: 0");
        String scan = step(build, "Scan for committed secrets");
        assertThat(scan).as("pinned release").containsPattern("GITLEAKS_VERSION: \\d+\\.\\d+\\.\\d+");
        assertThat(scan).as("pinned checksum").containsPattern("GITLEAKS_SHA256: [0-9a-f]{64}");
        assertThat(scan.indexOf("sha256sum -c")).as("verified before it runs")
                .isGreaterThan(0).isLessThan(scan.indexOf("gitleaks\" git"));
        assertThat(scan).as("found secrets are not printed into the log").contains("--redact");
    }

    @Test
    @DisplayName("The report script switches the tool's analytics off with the exact value true, the only value the tool honours (spec 007)")
    void reportScriptSwitchesAnalyticsOff() {
        List<String> assignments = code(readFile(REPORT_SCRIPT)).lines()
                .filter(l -> l.contains("ALLURE_NO_ANALYTICS="))
                .map(String::strip)
                .toList();

        assertThat(assignments)
                .as("restore 'export ALLURE_NO_ANALYTICS=true' in .github/scripts/report.sh; the tool leaves its Google Analytics tag in "
                        + "and reports to Google for any other value (even 1)")
                .isNotEmpty()
                .allMatch(l -> l.matches("(export\\s+)?ALLURE_NO_ANALYTICS=true"));
    }

    @Test
    @DisplayName("The README and the workflow build the report only through the script, never by calling the Maven report goals themselves (spec 007)")
    void reportIsBuiltOnlyThroughTheScript() {
        String readme = readFile(README).replaceAll("(?s)<!--.*?-->", "");
        String workflow = code(YAML);

        for (String goal : new String[]{"allure:report", "allure:aggregate", "allure:serve"}) {
            assertThat(readme).as("README text (code blocks included) must call .github/scripts/report.sh, not '" + goal + "': "
                    + "the script switches the tool's analytics off and refuses to build an empty report").doesNotContain(goal);
            assertThat(workflow).as("ci.yml must call .github/scripts/report.sh, not '" + goal + "'").doesNotContain(goal);
        }
    }

    @Test
    @DisplayName("Both check jobs keep their report result files with the run, so the Report job can collect them (spec 007 FS 2.6)")
    void checkJobsKeepTheReportResultFiles() {
        for (String id : new String[]{"api-tests", "ui-tests"}) {
            assertThat(step(job(id), "Keep the results"))
                    .as(id + ": restore 'target/allure-results/' in the path list of 'Keep the results'; without it the Report job "
                            + "finds no result files of this family")
                    .containsPattern("(?m)^            target/allure-results/$")
                    .contains("if: always()")
                    .contains("${{ github.run_attempt }}");
        }
    }

    @Test
    @DisplayName("The Report job waits for Build and both check jobs, runs after a failed or skipped check job, and is skipped after a broken build (spec 007 FS 2.6)")
    void reportJobRunsAfterTheCheckJobs() {
        String report = code(job("report"));
        assertThat(report).as("needs all three jobs: without 'build' the job cannot read needs.build.result, "
                        + "without a check job it could start before that job's result files are kept")
                .containsPattern("(?m)^    needs: \\[build, api-tests, ui-tests\\]$");
        assertThat(report).as("not cancelled AND Build succeeded: !cancelled() lets it run after a failed or skipped check job, "
                        + "the Build condition keeps it skipped (grey, not red) after a broken build")
                .containsPattern("(?m)^    if: \\$\\{\\{ !cancelled\\(\\) && needs\\.build\\.result == 'success' \\}\\}$");
    }

    @Test
    @DisplayName("A problem in the Report job can never turn the run red, and a missing report is explained on the run page (spec 007 FS 2.7)")
    void reportJobNeverChangesTheVerdict() {
        String report = job("report");
        assertThat(code(report)).as("job-level continue-on-error (4 spaces deep): the run's verdict comes from Build and the check jobs only")
                .containsPattern("(?m)^    continue-on-error: true$");

        String lastStep = report.substring(report.lastIndexOf("\n      - name: "));
        assertThat(lastStep).as("the last step runs even after a failed step, to say why there is no report")
                .containsPattern("(?m)^        if: always\\(\\)$");
        assertThat(lastStep).as("a warning annotation on the run page").contains("::warning title=Allure report missing::");
        assertThat(lastStep).as("one line in the job summary").contains(">> \"$GITHUB_STEP_SUMMARY\"");
    }

    @Test
    @DisplayName("The Report job builds the report through the script and keeps it under a name with the browser and the attempt, so a re-run does not replace it (spec 007 FS 2.6)")
    void reportIsBuiltByTheScriptAndKeptPerAttempt() {
        String report = job("report");
        assertThat(step(report, "Build the report")).as("the one documented way to build the report")
                .containsPattern("(?m)^        run: \\.github/scripts/report\\.sh$");
        assertThat(step(report, "Keep the report")).as("report artifact: browser as the UI tests job computes it, then the attempt number")
                .contains("name: allure-report-${{ inputs.browser || 'chromium' }}-${{ github.run_attempt }}")
                .contains("path: target/site/allure-maven-plugin/index.html");
        assertThat(report.indexOf("- name: Build the report")).as("built before it is kept")
                .isLessThan(report.indexOf("- name: Keep the report"));
    }

    @Test
    @DisplayName("The Report job links the kept report on the run page, from what the upload step gives back, and only when the report was kept (spec 007 FS 2.6)")
    void reportIsLinkedOnTheRunPageOnlyWhenKept() {
        String report = job("report");
        assertThat(step(report, "Keep the report")).as("the upload step has the id the link is read from").contains("id: keep");

        String lastStep = report.substring(report.lastIndexOf("\n      - name: "));
        assertThat(lastStep).as("the step that writes the link still runs after a failed step, to explain a missing report instead")
                .containsPattern("(?m)^        if: always\\(\\)$");
        assertThat(lastStep).as("the link is the address the upload step gives back for THIS run and attempt, never one put together by hand")
                .containsPattern("(?m)^          REPORT_URL: \\$\\{\\{ steps\\.keep\\.outputs\\.artifact-url \\}\\}$");
        assertThat(lastStep).as("the name in the line is the name the report is kept under")
                .containsPattern("(?m)^          REPORT_NAME: allure-report-\\$\\{\\{ inputs\\.browser \\|\\| 'chromium' \\}\\}-\\$\\{\\{ github\\.run_attempt \\}\\}$");
        assertThat(lastStep).as("the upload step's outcome is one of the reasons for 'no report'")
                .contains("KEPT: ${{ steps.keep.outcome }}").contains("elif [ \"$KEPT\" != \"success\" ]; then");

        String whenMissing = section(lastStep, "if [ -n \"$REASON\" ]; then", "\n          else\n");
        String whenKept = lastStep.substring(lastStep.indexOf(whenMissing) + whenMissing.length());
        assertThat(lastStep.substring(0, lastStep.indexOf(whenMissing)) + whenMissing)
                .as("no link outside the 'nothing is missing' branch: a report that was not built or not kept gets no link line")
                .doesNotContain("$REPORT_URL");
        assertThat(whenKept).as("one Markdown link to the kept report, with the number of checks it covers")
                .contains("($RESULT_FILES checks)").contains("[download $REPORT_NAME]($REPORT_URL)");
        assertThat(whenKept).as("written to the run page AND to the step's log, like the 'Summarise results' lines")
                .contains("echo \"$LINE\" | tee -a \"$GITHUB_STEP_SUMMARY\"");
    }

    @Test
    @DisplayName("The Report job checks the report's content after the results are gathered, without emptying them, and fails when the check skipped on a complete run (spec 008)")
    void reportJobChecksTheReportContent() {
        String report = job("report");
        String content = step(report, "Check the report's content");
        assertThat(code(content)).as("the post-run command of the README, run in the Report job")
                .contains("./mvnw -B -ntp -pl common,test-data,api-client test -Dtest=Spec008ReportContentAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false");
        assertThat(code(content)).as("no 'clean' and no '-am': either would empty the collected target/allure-results first")
                .doesNotContain("clean").doesNotContain("-am");
        assertThat(code(content)).as("a check that skipped itself on a complete run fails the step instead of passing silently")
                .contains("FAMILIES: ${{ steps.collect.outputs.families }}").contains("! grep -q ' skipped=\"0\"' \"$XML\"").contains("exit 1");
        assertThat(code(step(report, "Collect the newest results of each family")))
                .as("the check also compares the report with the Surefire reports and the run log, so they are put back from the artifact")
                .contains("cp -R \"$best/$module/target/.\" \"$module/target/\"")
                .contains("echo \"families=$FAMILIES\" >> \"$GITHUB_OUTPUT\"");
        assertThat(report.indexOf("- name: Keep the report")).as("after the report is kept, so a red check never costs the report")
                .isLessThan(report.indexOf("- name: Check the report's content"));
    }

    // ---- helpers -------------------------------------------------------------------------

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath() + " (tests must run with the module as working directory)", e);
        }
    }

    /** The text between two markers (end marker optional: to the end of the text). */
    private static String section(String text, String from, String to) {
        int s = text.indexOf(from);
        assertThat(s).as("marker '" + from + "'").isGreaterThanOrEqualTo(0);
        int e = text.indexOf(to, s + from.length());
        return e < 0 ? text.substring(s) : text.substring(s, e);
    }

    /** One job block: from its "  id:" line to the next job (or end of file). */
    private static String job(String id) {
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(id) + ":$").matcher(YAML);
        assertThat(m.find()).as("job '" + id + "' exists").isTrue();
        Matcher next = Pattern.compile("(?m)^  [a-z-]+:$").matcher(YAML);
        int end = YAML.length();
        if (next.find(m.end())) {
            end = next.start();
        }
        return YAML.substring(m.start(), end);
    }

    /** One step block: from its "- name: X" line to the next "- name:" (or end of job). */
    private static String step(String job, String name) {
        int s = job.indexOf("- name: " + name + "\n");
        assertThat(s).as("step '" + name + "' exists").isGreaterThanOrEqualTo(0);
        int e = job.indexOf("\n      - name:", s + 1);
        int c = job.indexOf("\n      #", s + 1);
        int end = job.length();
        if (e >= 0) end = Math.min(end, e);
        if (c >= 0) end = Math.min(end, c);
        return job.substring(s, end);
    }

    /** The text without comment lines, so a rule mentioned in a comment does not count. */
    private static String code(String text) {
        return text.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }
}
