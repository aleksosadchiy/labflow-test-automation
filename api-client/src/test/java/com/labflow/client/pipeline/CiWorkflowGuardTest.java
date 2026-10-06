// @layer: unit
// @spec: 006-ci-cd-pipeline
// @regression
package com.labflow.client.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A framework self-check about the build pipeline (spec 006): pins the decisions in
 * {@code .github/workflows/ci.yml} that cannot be exercised without a real GitHub run, so a
 * careless edit shows up in the lab-free Build job instead of as a silently weaker pipeline.
 * Reads the file as plain text (no YAML library, like the other guard tests); lives in
 * api-client because that module's tests run in the Build job and already have AssertJ.
 * Run-level behaviour (green runs, re-run, skipped jobs, artifacts) was proven on GitHub in the
 * slice Verify tasks and is not re-tested here.
 */
class CiWorkflowGuardTest {

    private static final Path CI = Path.of("..", ".github", "workflows", "ci.yml");
    private static final String YAML = read();

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
    @DisplayName("There are exactly three jobs named Build, Workflow checks and Screen checks, the two check jobs gated on Build (FS 2.2)")
    void jobsAndGate() {
        assertThat(job("build")).contains("name: Build");
        assertThat(job("api-tests")).contains("name: Workflow checks").containsPattern("(?m)^    needs: build$");
        assertThat(job("ui-tests")).contains("name: Screen checks").containsPattern("(?m)^    needs: build$");
        assertThat(job("build")).as("Build is the root of the graph").doesNotContain("needs:");
        assertThat(Pattern.compile("(?m)^  [a-z-]+:$").matcher(YAML.substring(YAML.indexOf("\njobs:")))
                .results().count()).as("number of jobs").isEqualTo(3);
    }

    @Test
    @DisplayName("Every job has a 15-minute timeout and a pinned runner image (FS 2.6)")
    void timeoutAndRunner() {
        for (String id : new String[]{"build", "api-tests", "ui-tests"}) {
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
            String choose = step(job, "Choose the access key");
            assertThat(choose).as(id + " masks before exporting")
                    .contains("::add-mask::$KEY").contains("API_TOKEN=$KEY\" >> \"$GITHUB_ENV\"");
            assertThat(choose.indexOf("::add-mask::")).isLessThan(choose.indexOf(">> \"$GITHUB_ENV\""));
        }
    }

    @Test
    @DisplayName("Build also packages the Mock Lab, so the lab's dependencies are in the Maven cache the check jobs restore (FS 2.6)")
    void buildWarmsTheLabCache() {
        assertThat(code(job("build"))).contains("cd mock-lab-app && ./mvnw -B -ntp -q package -DskipTests");
        assertThat(code(job("build"))).contains("cache: maven");
    }

    // ---- helpers -------------------------------------------------------------------------

    private static String read() {
        try {
            return Files.readString(CI);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + CI.toAbsolutePath() + " (tests must run with the module as working directory)", e);
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
