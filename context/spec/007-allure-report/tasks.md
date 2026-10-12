# Tasks: A Visual Report of Every Check Run (Allure Report)

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

After each slice:
- the branch `feature/allure-report` builds locally with `./mvnw`, `./mvnw spotless:check` is clean, and every existing check still passes against a running lab;
- running the checks without asking for a report behaves exactly as before (FS §2.1 ¶5);
- nothing under `mock-lab-app/` changes, and `api-client` gains no reporting dependency.

**Working conventions (used by every task):**

- **Spike draft.** Uncommitted draft files from an earlier spike are in the working tree (root `pom.xml`, `api-tests` and `ui-tests` poms and `allure.properties`, `AllureStepAppender` with its test, `AllureEvidence`, one line in `BrowserExtension`, `.gitignore`, `logback-test.xml`). The draft writes per-module results and records every test. Adapt it to the technical spec; do not take it as is and do not start from a clean tree.
- **Lab.** Checks need a running lab. Start one with `LAB_PORT=18080 .github/scripts/lab.sh start`, run with `-DbaseUrl=http://localhost:18080`, and stop it with `lab.sh stop`. Never touch a lab on 8080.
- **Maven.** Use `./mvnw` from the repo root, never `-q` when reading the `Tests run` lines, never `install`. There is no `timeout` binary: run long commands in the background with a capped polling loop, note `date +%T` before and after, and if a run exceeds 3 minutes capture `jstack` and stop it.
- **Results folder.** `target/allure-results` at the repo root is emptied by any Maven lifecycle command started from the root. Build the report straight after the test run.
- **Secrets.** A project hook blocks reading `.env` files and private keys; no task needs them. The lab's documented local key and password are the only values to scan for.
- **Evidence.** Quote real output for every claim: the `Tests run` and `Lab checks run: N` lines, file counts, and the lines found (or not found) by a scan.
- **Clean-up.** Delete only your own scratch files, by literal path. Stop any lab you started. Leave `target/` to `clean`.
- **GitHub (Slice 5 only).** `gh` is authenticated as `aleksosadchiy`. Never force-push, never push to `main`. A deliberate failure goes to a throwaway branch `ci-probe/<what>`, deleted locally and on `origin` afterwards. The repository is private with limited runner minutes: do not re-run more than the task asks.
- **Agent split.** `github-actions-cicd` owns Maven wiring and the workflow. `testing-expert` owns tests, guard tests and test configuration. `general-purpose` owns framework code under `src/main` and the base classes, because `testing-expert` does not edit non-test code, and the documentation.

---

- [x] **Slice 1: One Report of the Workflow Checks, Lab Checks Only**

  > After a workflow-check run, one command builds one self-contained report that lists exactly the lab checks, under their plain-language names, with verdict, duration and labels. Self-checks are absent. (FS §2.1, §2.2 ¶1–4, ¶6; TS §2.1–2.3, §2.6)

  - [x] Task: Maven wiring in the root `pom.xml`, adapting the spike draft:
    - Allure BOM 2.35.5 import and the three version properties, with the comment on why the 2.x line is used.
    - `allure-maven` 3.1.0 in `pluginManagement`: report tool 2.46.1, `singleFile`, results directory = the shared `target/allure-results` at the repo root, tool install directory = `.allure/` at the repo root.
    - One `maven-clean-plugin` execution on the root only (`inherited` false, phase `validate` (first bound to `initialize`; moved after the Slice 6 Verify run showed `./mvnw validate` then emptied nothing), default directories excluded) that empties the shared results folder and removes the previous built report.
    - `common/pom.xml`: `allure-junit-platform` at `provided` scope. `api-tests/pom.xml`: `allure-junit5` at test scope.
    - `.gitignore`: `.allure/`.

    **[Agent: github-actions-cicd]**
  - [x] Task: In `common`, move the "carries the tag `api` or `ui`" test into `Labels` and use it from `CheckCountListener`. Add `LabChecksAllureListener` (TS §2.2): it owns a private Allure JUnit Platform listener, always forwards plan start and finish, and forwards the per-identifier events only for identifiers that pass the shared tag test. Update `package-info.java`. **[Agent: general-purpose]**
  - [x] Task: Test configuration in `api-tests`:
    - `allure.properties` pointing at `../target/allure-results`;
    - `junit-platform.properties`: switch off Allure's own auto-registered listener with `junit.platform.execution.listeners.deactivate`;
    - register `LabChecksAllureListener` in the module's `TestExecutionListener` service file.
    - Unit-test the listener in `common` with a fake delegate: tagged identifiers forwarded, untagged not, plan start and finish always.
    - If the deactivate parameter does not switch the adapter's listener off, apply the fallback in TS §3 and record what was done in `technical-considerations.md`.

    **[Agent: testing-expert]**
  - [x] Task (added after the first Verify run): the built `index.html` loads one external script, Allure's own Google Analytics tag (`googletagmanager.com`), so opening the report makes a network request and, when online, reports to a third party. That contradicts FS §2.1 ¶3 and the "safe to share" intent of §2.5. Finding: the tool leaves the tag out, and skips an event it otherwise sends to Google each time a report is built, only when the environment variable `ALLURE_NO_ANALYTICS` is exactly `true`; the pom cannot set it. Owner's decision (2026-10-11): add `.github/scripts/report.sh`, the one documented way to build the report. It sets the variable, refuses with a clear message when there are no result files (so no empty report), runs `./mvnw -N allure:report`, and fails if the built `index.html` still references `googletagmanager`. Show that the built `index.html` holds no `http://` or `https://` reference to load. Record the decision in `technical-considerations.md`. **[Agent: github-actions-cicd]**
  - [x] Verify (lab on 18080):
    - `./mvnw -pl api-tests -am clean test -DbaseUrl=http://localhost:18080` is green, and the number of `*-result.json` files in `target/allure-results` equals the run's `Lab checks run: N`.
    - No result file names a self-check class (for example a `*GuardTest`), and every result's name is a `@DisplayName`.
    - `.github/scripts/report.sh` prints where the report is; `target/site/allure-maven-plugin/index.html` exists as one file and holds no external reference.
    - Run the same test command again and rebuild: the count is unchanged, not doubled (FS §2.1 ¶6).
    - `./mvnw clean` then `.github/scripts/report.sh` with no results, and again with an empty `target/allure-results` folder: a clear message, no `index.html` (FS §2.1 ¶4).
    - `./mvnw -pl common,test-data,api-client test` (no lab, no report asked) behaves as before.

    **[Agent: testing-expert]**

- [x] **Slice 2: A Workflow Check Shows What It Asked the Lab**

  > Opening a workflow check in the report shows its requests in order, each with kind, path, status, duration and identifiers, and the two families get a name the reader can group by. (FS §2.3, §2.2 ¶5; TS §2.4, §2.2)

  - [x] Task: Keep the drafted `AllureStepAppender` and its unit test, checking both against TS §2.4. Attach the appender to the `lab.http` logger in `api-tests/src/test/resources/logback-test.xml` only. **[Agent: testing-expert]**
  - [x] Task: Add `@Epic("Workflow checks")` to `LabTest`. **[Agent: general-purpose]**
  - [x] Verify (lab on 18080), after a workflow-check run:
    - The result of the full-journey check holds one step per request it made, in order, and each step text matches the run log line for that request in `api-tests/target/logs/api-tests.log`.
    - A check that makes no request has no steps and no error (FS §2.3 ¶3). If no such lab check exists, say so and quote the check with the fewest steps.
    - Every result carries the epic label "Workflow checks" and the tag labels `api` and `regression`.
    - The console shows no "no test is running" error from Allure during the pre-flight.

    **[Agent: testing-expert]**

- [x] **Slice 3: Screen Checks in the Same Report, with Evidence for a Failure**

  > One report now covers both families. A failed screen check shows its picture and its recording inside the report; a passing one shows neither. (FS §2.1 ¶1–2, §2.2 ¶5, §2.4; TS §2.5)

  - [x] Task: `ui-tests/pom.xml`: the Allure JUnit 5 adapter at test scope (adapt the draft). Maven warns that `allure-junit5` has been relocated to `allure-jupiter` (found in the Slice 2 Verify run): use `allure-jupiter` in both `api-tests/pom.xml` and `ui-tests/pom.xml`, and confirm the warning is gone and the same classes are on the classpath. **[Agent: github-actions-cicd]**
  - [x] Task: Test configuration in `ui-tests`, the same three items as in Slice 1 (`allure.properties`, the deactivate line, the service registration). Keep the drafted `AllureEvidence` and the one call from `BrowserExtension.saveFailureArtifacts`, checking both against TS §2.5. **[Agent: testing-expert]**
  - [x] Task: Add `@Epic("Screen checks")` to `UiTest`. **[Agent: general-purpose]**
  - [x] Verify (lab on 18080, Chromium installed):
    - `./mvnw clean test -fae -DbaseUrl=http://localhost:18080` is green; the result count equals the sum of the two modules' `Lab checks run: N`; both epics are present.
    - Make one screen check fail on purpose in the working tree (for example a wrong expected text), run `ui-tests`, and show that its result has two attachments (a PNG and a ZIP) and status failed with the failure message. Then restore the file and show `git status` is clean for it.
    - Open the downloaded trace attachment the documented way and confirm it steps through the check (FS §2.4 ¶3).
    - A passing screen check's result has no attachments.
    - `./mvnw -pl api-tests -am clean test …` then the report: only the workflow epic is present (FS §2.1 ¶2).
    - The existing self-tests that run a throwaway failing check through JUnit's test kit (`BrowserExtensionArtifactsTest`) are still green and add no result file.
    - Delete any trace or screenshot copied out of `target/` during the check.

    **[Agent: testing-expert]**

- [x] **Slice 4: Safe to Share, and Pinned by Guards**

  > Nothing in the result files or the built report holds the access key or the sign-in password, request content never reaches the report, and plain-text guards stop the wiring from drifting. (FS §2.5, §2.7 ¶3; TS §4)

  - [x] Task: Guard tests in the existing plain-text style:
    - one per test module: `junit-platform.properties` switches off Allure's own listener, the filtering listener is in the module's service file, and `allure.properties` points into `target/`;
    - one in `api-tests`: no module's `pom.xml` depends on `allure-rest-assured`;
    - one (next to `CiWorkflowGuardTest`, which already reads `.github/`): `report.sh` sets `ALLURE_NO_ANALYTICS` to exactly `true`, and neither the README nor `ci.yml` builds the report any other way than through that script.
    - Use fakes of the form `s3cret-…` if a made-up secret is needed.

    **[Agent: testing-expert]**
  - [x] Verify (lab on 18080):
    - Run the whole suite with `-DlogBodies=true`, build the report, and scan every file under `target/allure-results` and `target/site/allure-maven-plugin/index.html` for the lab's documented local access key and sign-in password: neither is found. Quote the scan commands and their counts.
    - Scan the same files for `Authorization` and for request or answer content (a `"name":` field): not found (FS §2.5 ¶3–4).
    - Any `TEST-` patient name found is inside a failure message or an attachment only; with a green run, none is found in text. Note: the unknown-sample checks use made-up sample identifiers of the form `TEST-unknown-sample-<uuid>`, which do appear in step texts and are not patient names; scan for patient names with a pattern that tells the two apart.
    - The number of checks and each verdict in the results equal the run's own output (FS §2.7 ¶3).
    - Break one guarded line on purpose (remove the deactivate line), show the guard goes red, and restore it.

    **[Agent: testing-expert]**

- [x] **Slice 5: The Report Is Kept with Every Automatic Run**

  > A fourth job, Report, collects the result files of both check jobs, builds the one report and keeps it as a downloadable file. It can never change the run's verdict. (FS §2.6, §2.7 ¶1–2; TS §2.7)

  - [x] Task: `.github/workflows/ci.yml`:
    - add `target/allure-results/` to the `path:` list of both "Keep the results" steps;
    - add the job `report` ("Report") exactly as TS §2.7 describes: its `needs` and `if`, job-level `continue-on-error`, download of the results artifacts, the per-family "highest attempt" copy step, `.github/scripts/report.sh`, the upload named `allure-report-<browser>-<attempt>`, and the final always-run step that writes a warning annotation and a summary line when `index.html` is missing;
    - pick the `actions/download-artifact` major that pairs with `upload-artifact@v7` from its release notes, and say which was chosen and why;
    - update the header comment of the file. Lint with `actionlint` (download the binary into the scratchpad).

    **[Agent: github-actions-cicd]**
  - [x] Task: Update `CiWorkflowGuardTest`: four jobs; `report` in the timeout and runner loop; pin the Report job's `needs`, `if` and `continue-on-error`, `run_attempt` in the report's artifact name, and `target/allure-results/` in both "Keep the results" steps. Keep comments above `- name:` lines, because the test's `step()` helper cuts a step at the first comment. **[Agent: github-actions-cicd]**
  - [x] Verify on GitHub (push `feature/allure-report`; quote run ids, job conclusions and the key log lines):
    - **Green run:** four jobs; the artifact `allure-report-chromium-1` exists; download it, unzip, and confirm `index.html` holds both families and opens from disk (FS §2.6 ¶1, ¶5). The job summaries of spec 006 are still there (¶7).
    - **One failed check job** (throwaway `ci-probe/` branch with one failing screen check): the run is red because of the check, the report is still kept and shows the failed check with its picture and recording next to the workflow checks (¶2).
    - **Hand-started single family** (`gh workflow run CI -f family=api`): the report holds the workflow checks only (¶3).
    - **Broken build** (throwaway branch with a compile error): Report is skipped, not failed (¶4).
    - **Re-run of the failed job** on the probe run: a second report `…-2` exists beside `…-1`, with no duplicated checks (¶6).
    - **Report cannot be built** (throwaway branch where the report step is made to fail): the run's verdict still comes from the checks, and the warning annotation names what is missing (FS §2.7 ¶1–2).
    - Delete every throwaway branch locally and on `origin`, and every downloaded artifact.

    **[Agent: github-actions-cicd]**

- [x] **Slice 6: Documented, Timed and Recorded**

  > The README says how to get and read the report, the architecture notes record the decision, and the run is shown to be at most 10% slower. (FS §2.8, §2.7 ¶4; TS §2.8)

  - [x] Task: README:
    - a "Visual report" part: the command, where the file is, how to open it, what it shows that the run-page summary does not, what it never contains, and that any new root Maven command empties the results;
    - "Continuous integration": four jobs, the report in the kept-files table, how to open a downloaded one, and that `Report` must not be a required check for merging.

    **[Agent: github-actions-cicd]**
  - [x] Task: Update the project notes:
    - `context/product/architecture.md` §1, §4 and §5: replace "_Alternative: Allure_ … later" with what was built and why (the 2.x line, the filtering listener, steps from the run log line instead of Allure's RestAssured filter, one shared results folder, the Report job);
    - `CLAUDE.md`: one convention line that reports reuse the run log line and never capture raw requests;
    - `context/product/hired-agents.md`: Allure is no longer an untaken alternative;
    - add the module README lines for the new listener (`common`) and the appender and evidence helper (`api-tests`, `ui-tests`).

    **[Agent: general-purpose]**
  - [x] Verify:
    - Time `./mvnw test -Dgroups=regression -DbaseUrl=…` on `main` and on this branch with the README's method (one warm-up, three timed runs, the median of Maven's `Total time`): the branch is at most 10% slower. Quote all six numbers. Use a scratch worktree for `main` and remove it afterwards.
    - Follow the README's "Visual report" part word for word on a clean checkout of the branch and confirm each command works as written.
    - Every path and command the README and the architecture notes mention exists.

    **[Agent: testing-expert]**

- [x] **Slice 7: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.
  - [x] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 007-allure-report` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [x] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**

- [x] **Slice 8: A Link to the Report on the Run Page** _(added 2026-10-12, after verification, at the owner's request)_

  > When the Report job has built and kept the report, the run page's summary shows one line with a direct link to it. With no report there is no link, only the existing "report missing" message. (FS §2.6, the three criteria added on 2026-10-12)

  - [x] Task: In the `report` job of `.github/workflows/ci.yml`, write one line into `$GITHUB_STEP_SUMMARY` (and into the step's log, as the "Summarise results" steps do) that says the report is ready, names how many checks it covers, and links to the report artifact of this run and attempt, using the URL the upload step gives back. No link when the report was not built or not kept. Find out from the action's documentation whether the single `index.html` can be uploaded unzipped so the link opens it instead of downloading a zip; adopt that only if it is supported and verified, and say what was verified. Pin the decisions in `CiWorkflowGuardTest`, update the README's "Continuous integration" part and `technical-considerations.md` §2.7, and lint with `actionlint`. **[Agent: github-actions-cicd]**
  - [x] Verify on GitHub: on a green push run of `feature/allure-report`, the Report job's log shows the summary line with a link, and the link's artifact id is the id of that run's `allure-report-…` artifact (checked through the API); on a hand-started `family=api` run the line names 48 checks. The "no report, no link" case is checked by a local dry run of the step's shell logic, not by another probe branch. Quote run ids and the lines. **[Agent: github-actions-cicd]**
