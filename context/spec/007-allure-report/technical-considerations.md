# Technical Specification: A Visual Report of Every Check Run (Allure Report)

- **Functional Specification:** [`functional-spec.md`](functional-spec.md)
- **Status:** Completed
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Allure works in two halves, and this design keeps them apart:

1. **Recording.** While the checks run, Allure's JUnit 5 adapter writes one small result file per check. Both check modules write into **one shared folder** at the repository root, `target/allure-results`.
2. **Building.** A separate, explicit command turns that folder into **one self-contained HTML file**. Nothing is built unless the engineer (or the CI Report job) asks for it.

Four things are added around the adapter, each small:

- A **filtering listener** so that only checks labelled `api` or `ui` are recorded. Allure's own listener records everything, including the framework's self-checks.
- A **Logback appender** in `api-tests` that turns each request line of the existing run log into an Allure step. The run log line is already free of secrets and names, so the report inherits that guarantee instead of needing its own.
- A **helper in `ui-tests`** that attaches the screenshot and Playwright trace that `BrowserExtension` already saves for a failed screen check.
- A fourth CI job, **Report**, that collects the result files of both check jobs, builds the single report and keeps it as an artifact. It can never change the run's verdict.

No Java agent (AspectJ) is used and Allure's RestAssured filter is deliberately not used. `api-client` stays free of any reporting library.

**Versions:** Allure adapter `io.qameta.allure:allure-bom` **2.35.5**, report plugin `io.qameta.allure:allure-maven` **3.1.0**, report tool Allure Report **2.46.1**. The 2.x adapter line is chosen on purpose: Allure 3.0.0 is built against JUnit 6, and this project is on JUnit 5.14. Dependabot's existing rule (ignore major versions) keeps it there.

**Specialists consulted:** `testing-expert` (adapter behaviour, checked against the 2.35.5 jars) and `github-actions-cicd` (job graph and artifacts). No separate codebase exploration was run: an earlier spike on this branch already established the facts recorded in section 2.9.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1. Files

| File | Change | Responsibility |
| --- | --- | --- |
| `pom.xml` (root) | edit | Allure BOM import; `allure-maven` in `pluginManagement` and declared on the root only; one `maven-clean-plugin` execution that empties the shared results folder (2.3); version properties. |
| `common/pom.xml` | edit | `allure-junit-platform` at `provided` scope, to compile the filtering listener (same pattern as the other JUnit APIs there). |
| `common/.../junit/LabChecksAllureListener.java` | new | The filtering listener (2.2). |
| `common/.../junit/Labels.java` | edit | One shared "is this a lab check" test on a set of tags, used by `CheckCountListener` and the new listener. |
| `api-tests/pom.xml`, `ui-tests/pom.xml` | edit | `allure-jupiter` at test scope (the adapter's current name; `allure-junit5` is a relocation stub that makes Maven warn). |
| `api-tests`, `ui-tests`: `src/test/resources/allure.properties` | new | Results folder: `../target/allure-results` (relative, because Surefire runs with the module as working directory). |
| `api-tests`, `ui-tests`: `junit-platform.properties` | edit | Switch off Allure's own auto-registered listener (2.2). |
| `api-tests`, `ui-tests`: `META-INF/services/…TestExecutionListener` | edit | Register the filtering listener. |
| `common/src/test/resources/junit-platform.properties` | new | The same deactivate line. `allure-junit-platform` is `provided` in `common`, so it is on `common`'s test classpath and its auto-registered listener would write an `allure-results` folder into the source tree during `common`'s own tests. `common` does not register the filtering listener. |
| `api-tests/.../support/AllureStepAppender.java` (+ test) | new | Request lines become steps (2.4). |
| `api-tests/src/test/resources/logback-test.xml` | edit | Attach the appender to the `lab.http` logger only. |
| `api-tests/.../support/LabTest.java`, `ui-tests/.../support/UiTest.java` | edit | `@Epic("Workflow checks")` / `@Epic("Screen checks")` (2.2). |
| `ui-tests/.../support/AllureEvidence.java` | new | Attach screenshot and trace (2.5). |
| `ui-tests/.../support/BrowserExtension.java` | edit | One call to the helper after the failure files are saved. |
| `.github/workflows/ci.yml` | edit | Upload result files from both check jobs; new Report job (2.7). |
| `api-client/.../pipeline/CiWorkflowGuardTest.java` | edit | Four jobs; pin the Report job's decisions (4). |
| `.github/scripts/report.sh` | new | The one documented way to build the report: switches the tool's analytics off, refuses to build without results, checks the built page (2.6). |
| `.gitignore` | edit | `.allure/` (the downloaded report tool). |
| `README.md`, `context/product/architecture.md`, `CLAUDE.md` | edit | Document the report; record the decision. |

A draft of several of these files already exists uncommitted on this branch from the spike. It writes per-module results and records every test, so it must be adapted to this design rather than taken as is.

### 2.2. Recording Only the Lab Checks (FS §2.2)

- Allure's adapter registers its listener, `io.qameta.allure.junitplatform.AllureJunitPlatform`, through `ServiceLoader`, and it records every test in the module. There is no supported tag filter in the adapter (its `AllurePostDiscoveryFilter` only reads a test-plan file).
- **Switch it off** with the JUnit Platform configuration parameter `junit.platform.execution.listeners.deactivate`, set to that class name, in each module's `junit-platform.properties`.
- **Register `LabChecksAllureListener`** instead. It owns a private `AllureJunitPlatform` and forwards to it:
  - `testPlanExecutionStarted` / `testPlanExecutionFinished`: always (they set up and clear the adapter's per-thread state);
  - `executionStarted`, `executionFinished`, `executionSkipped`, `reportingEntryPublished`: only when the identifier carries the tag `api` or `ui`. The test is on tags, not on "is a test", because a failing `@BeforeAll` or a skipped class is reported through its container.
- The tag test moves into `Labels` so `CheckCountListener` and this listener cannot drift apart. That also makes FS §2.7's "the report holds as many checks as the run says it executed" true by construction: both count with the same rule.
- **Telling the families apart:** `@Epic` on the two base classes. `@Epic` is `@Inherited` and the adapter reads class annotations with `getAnnotations()`, so every check inherits it. The report's Behaviors view then groups by family. The JUnit tags (`api`, `ui`, `smoke`, `regression`) already become Allure `tag` labels with no extra code.
- **Names:** the adapter takes the display name, so every check and every parameterized row appears under its `@DisplayName`. `PlainRowNamesAcceptanceTest` already guarantees row names carry no argument values.

_Rejected:_ deleting unwanted result files after the run (fragile, and the count would be wrong for a moment); running the report only from a tag-filtered run (CI runs `regression | !(api | ui)` on purpose).

### 2.3. One Results Folder per Run (FS §2.1)

- Both modules write to `target/allure-results` at the repository root, so one report covers both families with no aggregation step. **Confirmed by the owner (2026-10-11)**, including the consequence below.
- **A run starts empty.** One `maven-clean-plugin` execution on the root project (`<inherited>false</inherited>`, phase `validate`, the first phase of the default lifecycle, `excludeDefaultDirectories`, one fileset per folder) deletes the result files and the previous built report (`target/site/allure-maven-plugin`). The report goes too because, with no results, the report goal builds nothing but leaves an old `index.html` in place, which could be mistaken for the new run's. The root project is first in the reactor for every documented command, including `-pl api-tests -am` and `-pl ui-tests -am` (the reactor order was checked by running `validate` with each). The phase was `initialize` at first; the Slice 6 Verify run found that `./mvnw validate` then emptied nothing, because `initialize` comes after `validate`, so the execution was moved to `validate`. So a second run never mixes with the first, and a single-family run leaves nothing from the other family.
- **Consequence to document:** any Maven lifecycle command whose reactor includes the root project (even `validate`) empties the folder and removes the built report; that is every documented command for running checks, but not a `-pl` selection without `-am` (for example CI's `-pl common,test-data,api-client`), which leaves the root out. The report command itself (`allure:report`, a direct goal with no lifecycle) does not.
- `clean` removes the folder as well, because it is under `target/`.

### 2.4. Request Steps for Workflow Checks (FS §2.3, §2.5)

- `AllureStepAppender` (a Logback `AppenderBase`) is attached to the `lab.http` logger in `api-tests` only. For an event at INFO or above, and only when Allure has a current test case on this thread, it calls `Allure.step(message)`.
- The message is the line `RequestLogFilter` already writes: method, path, status, duration, and the patient/order/sample identifiers. It never holds headers, query strings, names or bodies.
- Bodies (`-DlogBodies=true`) are logged at DEBUG and are therefore never forwarded.
- **Thread affinity:** Allure tracks the current test case per thread. `executionStarted` runs on the worker thread that executes the check, and a workflow check's body runs on that same thread, so the step lands on the right check under parallel execution.
- Screen checks are not given request steps: their body runs on a separate thread (the `SEPARATE_THREAD` timeout), and FS §2.3 asks for requests on workflow checks only.

_Rejected:_ `allure-rest-assured`'s filter. It attaches the raw request and answer, including the `Authorization` header and patient names.

### 2.5. Evidence for a Failed Screen Check (FS §2.4)

- `BrowserExtension.saveFailureArtifacts` already saves `screenshot.png` and `trace.zip` in `afterEach`. After the saves, it calls `AllureEvidence.attach(folder)`.
- The helper attaches whichever of the two files exists (`Allure.addAttachment` with a name, media type and extension). It does nothing when Allure has no current test case (the self-tests that run a throwaway check through JUnit's test kit), and it logs and continues if a file cannot be read. A missing picture therefore never hides the failure itself.
- `afterEach` runs on the worker thread before `executionFinished`, so the test case is still open there.
- A passing check saves nothing, so nothing is attached.

### 2.6. Building the Report (FS §2.1)

- `allure-maven` is configured once: report tool version, `singleFile` = true, results directory = the shared folder, tool install directory = `.allure/` at the repository root.
- Command, from the repository root: `.github/scripts/report.sh`. **Chosen by the owner (2026-10-11)** over the bare `./mvnw -N allure:report`, which the script runs. Output: `target/site/allure-maven-plugin/index.html`, one self-contained file that opens from disk with no server and no network. On success the script prints the file's absolute path and the number of checks it covers.
- **Analytics off.** Allure Report 2.46.1 puts a Google Analytics tag (`googletagmanager.com`) into the page and also sends a `report_generated` event to Google each time a report is built (`GaPlugin` in the generator jar). Both are switched off only by the environment variable `ALLURE_NO_ANALYTICS`; the tool reads no system property, properties file or config entry for it. The value must be exactly `true`: the page tag is decided with `Boolean.parseBoolean`, so `1` stops the event but leaves the tag in (seen in a build). `allure-maven` 3.1.0 has no parameter for the variable and starts the tool with the environment it inherits, so the root `pom.xml` cannot set it. The script exports it.
- **No results, no report.** With a missing results folder the plugin builds nothing, but with a folder that is present and empty it writes an empty report (found in the first Verify run). The script therefore counts the `*-result.json` files itself: with none it says so, names the command that runs the checks, removes a stale `index.html` of an earlier run (that one file only) and exits 1.
- **Checked after the build.** The script fails if `index.html` is missing or still holds `googletagmanager`, so a tool version that stops honouring the variable is noticed.
- `ALLURE_NO_ANALYTICS=true ./mvnw -N allure:serve` remains available for a served view; the variable has to be given by hand there.

### 2.7. CI: the Report Job (FS §2.6, §2.7)

**Confirmed by the owner (2026-10-11):** a fourth job, after weighing the trade-off at the end of this section.

- **Check jobs:** add `target/allure-results/` to the `path:` list of each existing "Keep the results" step. Those steps already run with `if: always()`, carry `run_attempt` in the name and use the default retention, so no new artifact and no change to the guard's "no `retention-days`" rule.
- **New job** `report`, name `Report`, `ubuntu-24.04`, 15-minute timeout:
  - `needs: [build, api-tests, ui-tests]` and `if: ${{ !cancelled() && needs.build.result == 'success' }}`. It runs when a check job failed or was skipped by a hand-started run, and is skipped (grey, not red) when Build failed.
  - `continue-on-error: true` at job level: the guarantee that a reporting problem cannot turn the run red.
  - Steps: checkout; setup-java with the Maven cache; download the two results artifacts with `actions/download-artifact` (pattern per family, each artifact into its own folder under `$RUNNER_TEMP`); a short shell step that, per family, takes the folder with the **highest attempt number** and copies its `target/allure-results` into the workspace; `.github/scripts/report.sh`; upload `index.html` as `allure-report-<browser>-<attempt>`.
  - A last step with `if: always()` checks whether `index.html` exists. If not, it writes a `::warning title=Allure report missing::<reason>` annotation and one line in `$GITHUB_STEP_SUMMARY`.
- **Verified while implementing (2026-10-11), from the actions' own README and source, not yet from a GitHub run:**
  - `actions/download-artifact@v8` is used. v8.0.0 and `upload-artifact` v7.0.0 were released together (2026-02-26, "to support direct uploads in `actions/upload-artifact`"), and the v8 README pairs the two in its examples. Inputs used: `pattern` and `path`. No `name`, because the newest attempt number of the other family is not known in a re-run.
  - **Artifact layout:** with several `path` entries, "the least common ancestor of all the search paths will be used as the root directory of the artifact" (upload-artifact README). The ancestor here is the repository root, so the result files sit at `target/allure-results/` inside each results artifact.
  - **Download layout depends on the number of matches** (`src/download-artifact.ts` at tag `v8`): several matching artifacts each go into a sub-folder named after the artifact, but **exactly one match is unpacked straight into `path`**, with no sub-folder. One match per family is the normal first attempt. So each family is downloaded into its own folder, and the copy step accepts both layouts. A pattern that matches nothing is not an error.
  - **Where `continue-on-error` sits:** on the job only, on no step. A step that breaks turns the Report job red and the run's verdict stays untouched. With no result files at all the build and upload steps are skipped by their `if`, so the job stays green with the warning: nothing in reporting broke, and the check job that kept no results is red already.
- **Link on the run page (added 2026-10-12, FS §2.6 last three criteria):** when nothing is missing, the last step (the one that otherwise explains a missing report) writes one line with `| tee -a "$GITHUB_STEP_SUMMARY"`: the number of checks (the count of result files from the collect step) and a Markdown link to the report artifact. It is in that step and not in one of its own because that step already decides "report or no report" from every outcome, so the link and the "missing" line cannot both appear, and no second step can turn the job red. A failed write is caught in the shell (`|| echo`), because the report is kept by then. The link is the upload step's output `artifact-url`, so it belongs to this run and attempt. If that output is empty although the upload succeeded, the line names the artifact and says there is no link, instead of printing a broken one. Verified from `actions/upload-artifact` v7.0.2 (`action.yml` and README), not yet from a GitHub run:
  - outputs `artifact-id`, `artifact-url` ("A download URL for the artifact that was just uploaded. Empty if the artifact upload failed."; example `https://github.com/example-org/example-repo/actions/runs/1/artifacts/1234`; "Users must be logged-in in order for this URL to work") and `artifact-digest`;
  - **the report stays zipped.** v7 has `archive: false` for one unzipped file, but "the name of the file will be used as the artifact name (ignoring the `name` parameter)". The artifact would be called `index.html`: no browser and no attempt in the name, and a second attempt would collide with the first, because without `overwrite` "the action will fail if an artifact for the given name already exists". The documentation also calls the address a download URL and nowhere says a browser shows such a file instead of saving it. So the link downloads the zip and the line says so.
- **Re-runs:** artifacts belong to the run, so attempt 2 also sees attempt-1 artifacts. Taking the highest attempt per family gives the fresh results of the re-run job plus the attempt-1 results of the other, with no duplicates. The report's own name carries the attempt, so attempt 1's report is not replaced.
- **Permissions:** unchanged (`contents: read`). Downloading artifacts of the same run needs no extra permission.
- **Why a fourth job and not a step:** the two check jobs run on different machines, so neither has the other's result files. One combined report therefore needs something that runs after both. The alternatives are worse: running both families in one job loses spec 006's separate, separately re-runnable jobs; building two reports contradicts the "one combined report" decision; building none in CI drops FS §2.6. The job is cheap (about one runner-minute) and cannot fail the run.
- **Not required for merging:** the README's branch-protection advice keeps `Build`, `Workflow checks` and `Screen checks` and says `Report` must not be a required check.

### 2.8. Documentation (FS §2.8)

- README: a "Visual report" part (the command, where the file is, how to open it, what it shows that the summary does not, what it never contains); "Continuous integration" becomes four jobs, with the report artifact in the kept-files table.
- `architecture.md` §5: replace "_Alternative: Allure_ … later" with the decision and its reasons. `CLAUDE.md`: one line that reports go through the existing run log line, never through raw request capture.
- `hired-agents.md` lists Allure as a gap; no new specialist is needed (see section 5 of the tasks hand-off below).

### 2.9. Facts Established by the Spike

- `allure-junit5` 2.35.5 (later switched to the artifact's current name, `allure-jupiter`; the old one is a relocation stub) runs on JUnit 5.14 with no agent; a full `api-tests` run wrote 79 result files, 48 of them lab checks.
- A parameterized check's result holds a `UniqueId` parameter only, not its argument values.
- No result file contained the access key, the sign-in password, a `TEST-` patient name or the word `Authorization`.
- `allure-maven` builds a single-file report of about 3 MB in about 7 s; `resultsDirectory` must be relative for `allure:aggregate`.

---

## 3. Impact and Risk Analysis

**System dependencies**

- `common` gains one more `provided` dependency (`allure-junit-platform`). `api-client` and `test-data` are untouched.
- The CI workflow goes from three jobs to four; `CiWorkflowGuardTest` and the README's job description change with it.
- The run log line from `RequestLogFilter` becomes an input to the report. A future change to that line changes the report.

**Risks and mitigations**

| Risk | Mitigation |
| --- | --- |
| `junit.platform.execution.listeners.deactivate` does not switch off the auto-registered listener as expected (not verified against a jar), so self-checks appear or checks are recorded twice. | First slice proves it: result count must equal `Lab checks run: N`. Fallback: exclude the adapter's service registration by depending on `allure-junit-platform` + `allure-java-commons` directly and registering only our listener. **Verified (slice 1):** the parameter works from `junit-platform.properties` on JUnit 5.14 with Allure 2.35.5, so the fallback was not needed: `api-tests` wrote 48 result files for `Lab checks run: 48`, none from a self-check class, and `common`'s tests write no `allure-results` folder. |
| A failure message or stack trace in a result file echoes data. AssertJ and Playwright messages can quote a response or page text, including a made-up `TEST-` patient name. | These are synthetic names by design (spec 006 already accepts them in failure evidence, and Surefire reports and the job summary already carry the same messages). Real secrets stay out because checks never assert on the key or the password. The acceptance pass scans every result file and the built HTML for the key and the password, with `-DlogBodies=true`. **Confirmed by the owner (2026-10-11):** made-up `TEST-` names may appear in failure messages; FS §2.5 says so. |
| Container events arrive on a different worker thread than they started on, so the adapter's per-thread bookkeeping misses. | Not seen in the spike (79 of 79 results). The acceptance pass compares the result count with the run's own count under the default parallelism. |
| The shared results folder is emptied by any root lifecycle command, which may surprise an engineer who compiles between running and reporting. | Documented in the README next to the command. |
| The single-file report grows when many screen checks fail (pictures and traces are embedded). | Acceptable at this suite's size (16 screen checks). Noted in the README; the trace is also still in the `screen-failures` artifact. |
| The report tool is downloaded from Maven Central at build time, pinned by version but not checksum-verified. | Accepted for a reporting tool that touches no secret; said so in a comment. Only the Report job downloads it. |
| The report tool has Google Analytics built in: a tag in the page (a network request whenever the report is opened) and an event sent to Google on every build. That contradicts FS §2.1 ¶3 and the intent of FS §2.5. | `.github/scripts/report.sh` exports `ALLURE_NO_ANALYTICS=true` and fails if the built page still holds `googletagmanager`; a guard test pins the value `true` (not `1`). **Verified (slice 1):** with the variable the page holds no `http(s)://` reference and the tool logs `analytics is disabled`. **Not verified:** the exact content of the event, and that no request leaves the machine (this rests on reading the bytecode and on that log line; no network capture was made). Someone who runs the bare `allure:report` goal still gets the tag and sends the event. |
| The Report job shows red while the run is green (`continue-on-error`), which can confuse. | The final step's warning annotation and summary line say what is missing and why. |
| Reporting slows the run beyond FS §2.7's 10%. | Measured in the last slice with the same three-run median method as the README's timing table. The adapter only writes small JSON files. |

---

## 4. Testing Strategy

Proportionate to a reference project: a few fast tests that pin the decisions, and one lab-backed acceptance pass.

- **Unit, `common`:** `LabChecksAllureListener` with a fake delegate. Tagged identifiers are forwarded, untagged ones are not, plan start and finish are always forwarded.
- **Unit, `api-tests`:** `AllureStepAppender` (drafted). An INFO line becomes a step, a DEBUG line does not, nothing happens when no check is running.
- **Guard, one per test module (plain-text style, like the existing guards):** `junit-platform.properties` switches off Allure's own listener, the filtering listener is in the module's `ServiceLoader` file, and `allure.properties` points into `target/`. This catches a silent return to "every self-check is reported".
- **Guard, `api-tests`:** `allure-rest-assured` is not a dependency of any module.
- **Guard, `CiWorkflowGuardTest`:** four jobs; the Report job's `needs`, `if` and `continue-on-error`; `run_attempt` in the report's artifact name; `target/allure-results/` in both "Keep the results" steps.
- **Acceptance pass (lab-backed, in the Verify task, by command and by reading the files):** result count equals `Lab checks run: N`; a workflow check has one step per request; a deliberately failed screen check has a picture and a trace attached and a passing one has none; a text scan of the result files and of `index.html` finds neither the access key nor the sign-in password, also with `-DlogBodies=true`; the report opens from disk with the network off; a single-family run reports one family; no results means no report.
- **CI, on GitHub:** a green run, a run with one failed check job, a hand-started single-family run, a failed build, and a re-run of a failed job, each checked against FS §2.6 and §2.7 the way spec 006's Verify tasks were.

---

## 5. Hand-off Notes for `/awos:tasks`

- Suggested first slice: recording with the filter in `api-tests` only, proving the deactivate parameter and the count. Everything else depends on it.
- Agents: `testing-expert` for the listener, appender, evidence and guards; `github-actions-cicd` for the Report job and `CiWorkflowGuardTest`. No new specialist is needed.
