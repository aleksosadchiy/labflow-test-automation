# Functional Specification: A Visual Report of Every Check Run (Allure Report)

- **Roadmap Item:** None. The roadmap is complete; this is an addition made after it. Spec 006 listed "richer report formats or dashboards beyond the run-page summary (for example Allure)" as out of scope, and this specification picks that item up.
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

After a run, the framework already tells the engineer how many checks passed, failed and were skipped, and for a failed check its name and failure message. To learn more, the engineer has to open several places: a text log for what a workflow check asked the lab, a folder of files for a failed screen check's picture and recording, and the hosting service's run page for the totals. Nothing shows one check from start to finish in one place, and nothing can be handed to a reviewer who does not want to read logs.

This specification adds a **visual report**: one page, built with the widely used Allure Report tool, that lists every check of a run by its plain-language name, shows its verdict and duration, and lets the reader open any single check to see what it did. A workflow check shows the requests it made to the lab, in order. A failed screen check shows its picture and its recording. The report opens by double-clicking one file, with nothing to install.

There is one report per run, covering both check families, and it lists only the checks that test the lab.

In this specification the **engineer** is whoever runs the checks or reviews a change (in practice the author, or a reviewer looking at the repository). The **report** is the visual report described here. A **check family** is either the workflow checks (through the lab's connected interface) or the screen checks (through its screens in a browser). The **run page** is the page the hosting service shows for one automatic run. The **lab** is the Mock Lab.

Success is measured by four things:

- After any run, the engineer can open one page and see every check with its verdict, without reading a log.
- A failed check can be understood from the report alone: its message, and for a screen check its picture and recording.
- The report can be shared as it is: it never contains the access key or the sign-in password, and the only patient names in it are made-up ones starting with "TEST-".
- Adding the report changes no verdict and does not slow the engineer's normal run noticeably.

---

## 2. Functional Requirements (The "What")

### 2.1. One Report per Run, Opened with a Double-Click

- **As an** engineer, **I want** to build one report after I have run the checks on my machine, **so that** I can look through the results of both check families in one place instead of scrolling a console.
  - **Acceptance Criteria:**
    - [x] When the engineer has run both check families and then gives the one documented command to build the report, then a single report covering the workflow checks and the screen checks is created in a documented place and the command's output says where it is.  _(verified 2026-10-12: `./mvnw clean test` then `.github/scripts/report.sh` printed `Allure report: …/target/site/allure-maven-plugin/index.html (64 checks)`; see `docs/screenshots/007-allure-report-two-families.png` (Screen checks 16, Workflow checks 48))_
    - [x] When the engineer has run only one check family and then builds the report, then the report shows that family's checks and nothing from the family that was not run.  _(verified: after `./mvnw -pl ui-tests -am test` the report held `(16 checks)` under "Screen checks" only; after `-pl api-tests -am` 48 under "Workflow checks" only (Slice 3 and Slice 6 Verify))_
    - [x] When the engineer double-clicks the report file, then it opens in their browser and shows its content, without starting any program and without an internet connection.  _(verified: Chromium opened the file from disk with no server (the five screenshots under `docs/screenshots/007-allure-report-*.png`); the file holds 0 `http(s)://` references and no analytics tag; opening by double-click with the network off confirmed by the owner on 2026-10-12)_
    - [x] When the engineer builds the report before any check has been run, then they see a message that there are no results to report, and no empty report is presented as if it were a real one.  _(verified: after `./mvnw validate`, `report.sh` printed `No results to report: target/allure-results holds no *-result.json file.` and `Run the checks first …`, exit code 1, no `index.html`; pinned by `Spec007ReportAcceptanceTest`)_
    - [x] When the engineer runs the checks without asking for a report, then the run behaves exactly as before and no report is built.  _(verified: a run without the script builds no `index.html`; the same checks pass with the same counts (16 and 48), and the regression run is 1.8% slower (33.6 s against 33.0 s, medians of three, Slice 6 Verify))_
    - [x] When the engineer runs the checks a second time and builds the report again, then the report shows only the second run's results.  _(verified: a run with one failed screen check was followed by a green run; the second report held 64 checks, none failed and no attachment)_

### 2.2. Every Check Listed by Its Plain-Language Name

- **As a** reviewer, **I want** the report to list the checks the way the project describes them, **so that** I can read it without knowing the code.
  - **Acceptance Criteria:**
    - [x] When the engineer opens the report, then its first screen shows the total number of checks, the share that passed and how long the run took, and the list of checks shows how many passed, failed and were skipped.  _(verified, criterion reworded (see Change Log): `docs/screenshots/007-allure-report-overview.png` shows 64 test cases, 100% and `11s 998ms`; `…-two-families.png` shows the Status counts 0 / 0 / 64 / 0 / 0)_
    - [x] When the engineer opens the list of checks, then every check appears under its plain-language name (for example "A registered sample starts as Accessioned"), and a check that runs once per row of data appears once per row, each under that row's own name.  _(verified: 64 names in the report, none a Java identifier; parameterized rows appear once per row under the row's name; see `…-workflow-check-steps.png`; pinned by `LabChecksRecordedAcceptanceTest` and `PlainRowNamesAcceptanceTest`)_
    - [x] When the engineer looks at one check, then they see its verdict, how long it took, and its labels (workflow or screen, smoke, regression).  _(verified: `…-workflow-check-steps.png` shows Passed, Duration 617ms and the tags regression, smoke, api; `…-failed-screen-check.png` shows Failed, 195ms, ui, regression)_
    - [x] When the engineer filters the list by verdict, then only the checks with that verdict are shown.  _(verified: the Status filter (failed / broken / passed / skipped / unknown) is on the list, see `…-two-families.png` and `…-failed-screen-check.png` (1 failed, 15 passed))_
    - [x] When the engineer looks at the list of checks, then they can tell the workflow checks from the screen checks, and can view either family on its own.  _(verified: the list groups the checks as "Screen checks" and "Workflow checks", each expandable on its own (`…-two-families.png`))_
    - [x] When the engineer looks through the whole list, then it holds only the checks that test the lab, and none of the framework's own self-checks (the checks that test the framework itself).  _(verified: 64 results for `Lab checks run: 16` + `48`, while the modules ran 110 and 87 tests; no self-check class in any result; pinned by `LabChecksAllureListenerTest`, `LabChecksRecordedAcceptanceTest` and the wiring guards)_

### 2.3. A Workflow Check Shows What It Asked the Lab

- **As an** engineer, **I want** each workflow check in the report to show its conversation with the lab, **so that** I can see what happened without searching the log.
  - **Acceptance Criteria:**
    - [x] When the engineer opens a workflow check in the report, then they see the requests it made to the lab in the order they were made.  _(verified: the full-journey check lists its 15 requests in order (`…-workflow-check-steps.png`); 209 steps across 48 checks matched the run log line for line (Slice 2 Verify))_
    - [x] When the engineer reads one request in that list, then it shows the kind of request, the lab address path, the answer's status, how long it took, and the identifiers involved (patient, order and sample identifiers).  _(verified: every one of 209 steps has the shape `METHOD /path -> STATUS in N ms [identifiers]`, e.g. `POST /api/samples -> 201 in 5 ms [patient=… order=… sample=…]`)_
    - [x] When a workflow check made no request to the lab, then the report shows that check without a request list and without an error.  _(verified in part: every lab workflow check makes at least one request, so no real one exists; a check with no request lines renders with no list and no error, as the 16 screen checks do (`…-failed-screen-check.png`); the no-step path is covered by `AllureStepAppenderTest`)_

### 2.4. A Failed Check Explains Itself

- **As an** engineer, **I want** a failed check's evidence inside the report, **so that** I do not have to look for files on disk.
  - **Acceptance Criteria:**
    - [x] When a check fails, then the report shows its failure message, including what was expected and what was found.  _(verified: `…-failed-screen-check.png` shows `expected: "Invalid username or password (WRONG)." but was: "Invalid username or password."`)_
    - [x] When a screen check fails, then the report shows, inside that check, the picture of the screen at the moment of failure and a downloadable recording of the check.  _(verified: the same screenshot shows "Screenshot at the moment of failure" (14.8 KiB) and "Playwright trace (open at trace.playwright.dev)" (34.9 KiB) inside the failed check)_
    - [x] When the engineer downloads that recording from the report and opens it the documented way, then they can step through what the check did on the screens.  _(verified: the trace archive holds `trace.trace`, `trace.network`, screencast frames and 9 recorded actions (Slice 3 Verify); stepping through it in the trace viewer confirmed by the owner on 2026-10-12)_
    - [x] When a screen check passes, then the report shows no picture and no recording for it.  _(verified: the 15 passing screen checks of the same run have no attachment; a green run has no attachment file at all)_
    - [x] When the picture or the recording could not be saved for a failed screen check, then the report still shows that check as failed with its failure message.  _(verified 2026-10-12: with the evidence folder blocked, the check's result was `status=failed`, 0 attachments, and its message was still the assertion's expected-versus-found text)_

### 2.5. Nothing Sensitive in the Report

- **As an** engineer working on a regulated-style system, **I want** the report to be safe to hand to anyone, **so that** sharing it never leaks a secret or a patient's details.
  - **Acceptance Criteria:**
    - [x] When the engineer searches every file of a report for the lab's access key, then it is not found.  _(verified: 0 occurrences in all result files and in the decoded report, with a control string found 39 times (run with `-DlogBodies=true`))_
    - [x] When the engineer searches every file of a report for the sign-in password, then it is not found, including for the checks that sign in or try a wrong password.  _(verified: 0 occurrences in the result files, the decoded report, and the failed sign-in check's picture and trace (Slice 3 and Slice 4 Verify, repeated 2026-10-12))_
    - [x] When the engineer reads the requests listed for a workflow check, then they see identifiers only, and no patient name and no content of what was sent or received.  _(verified: step texts hold identifiers only; 0 occurrences of `Authorization`, of a patient name and of body content; pinned by `NoAllureRequestCaptureGuardTest`)_
    - [x] When the engineer switches on the option that adds request and answer content to the run log, then that content still does not appear in the report.  _(verified: the run had 209 `request body:` lines in its log and none in the report; pinned by `AllureStepAppenderTest`)_
    - [x] When a failed screen check's picture or recording shows a patient's name, then it is a made-up name starting with "TEST-", exactly as the screen showed it.  _(verified: checks can only create patients named `TEST-…` (`CheckIndependenceGuardTest`); no seeded patient name was found in any report file; the failed check captured here shows no patient at all)_
    - [x] When a failed check's failure message quotes a patient's name, then it is a made-up name starting with "TEST-", the same message the run's existing summary already shows.  _(verified: the only failure message produced quotes no patient; names in messages can only be `TEST-…` for the same reason)_

### 2.6. The Report Is Kept with Every Automatic Run

- **As a** reviewer, **I want** the report of an automatic run to be available from its run page, **so that** I can inspect a run I did not start.
  - **Acceptance Criteria:**
    - [x] When both check jobs of an automatic run finish with every check passed, then one report covering both families is listed among that run's kept files, under a name that says it is the report and which browser the screen checks used.  _(verified: run 38195596695 (push, green): Build, Workflow checks, Screen checks, Report all success; artifact `allure-report-chromium-1` with a 64-check report)_
    - [x] When one check job finishes with a failed check, then the report is still kept with the run and shows the failed check together with the checks of the other family.  _(verified: run 38173108099 (one failed screen check): run failure, Report success, report kept with 1 failed and 63 passed, both families, picture and trace inside)_
    - [x] When a run was started by hand for one check family only, then the kept report shows that family's checks and nothing else.  _(verified: run 38173112019 (hand-started, family=api): Screen checks skipped, Report success, 48 checks, workflow family only)_
    - [x] When the build fails and neither check job runs, then no report is kept and the run page does not show a failed reporting step as the cause.  _(verified: run 38173110158 (compile error): Build failure, both check jobs and Report skipped, no report artifact)_
    - [x] When the reviewer downloads the kept report and double-clicks its file, then it opens in their browser as described in 2.1.  _(verified: the downloaded `index.html` is one self-contained file with no external reference (Slice 5 Verify); opening it by double-click confirmed by the owner on 2026-10-12)_
    - [x] When a failed check job is run a second time, then the second attempt's report is kept without replacing the first attempt's.  _(verified: run 38173108099 attempt 2 has `allure-report-chromium-1` and `allure-report-chromium-2`; the second holds 64 checks with no duplicate)_
    - [x] When the run page is opened after this change, then the short summary of each job (passed, failed, skipped, duration) is still there as before.  _(verified: the "Summarise results" steps still print their lines, e.g. `✅ **Workflow checks**: 87 passed, 0 skipped` (run 38195596695))_
    - [x] When the reviewer looks for the report anywhere other than the run's kept files, then they find none: the report is not published as a web page.  _(verified: the workflow has no publishing step and only `contents: read`; the report exists only as a run artifact; pinned by `CiWorkflowGuardTest`)_
    - [x] When an automatic run has built its report, then the summary shown on the run page contains one line that says the report is ready and gives a link to it.  _(verified 2026-10-12: run 38227971213 (push), Report job, printed and wrote to the summary `📊 **Allure report** of this run (64 checks): [download allure-report-chromium-1](…/actions/runs/38227971213/artifacts/11736259898) (a zip: unzip it and double-click index.html)`; run 38227988858 (hand-started, one family) printed the same line with 48 checks)_
    - [x] When the reviewer follows that link, then they get the report of that same run and attempt, and it opens as described in 2.1.  _(verified: the linked artifact id 11736259898 is the id of that run's `allure-report-chromium-1` artifact (API), and downloading it gave one `index.html` with 64 checks, all passed, and no external reference; the link downloads a zip, which the line says; clicking it in a browser is left to the owner)_
    - [x] When an automatic run built no report, then the summary shows no such link and still says the report is missing and why.  _(verified by a dry run of the step's own script in each no-report case (no result files, script failed, upload failed): the warning and the reason, no link line; pinned by `CiWorkflowGuardTest.reportIsLinkedOnTheRunPageOnlyWhenKept`; the earlier run 38173111334 showed the missing-report warning on GitHub; no new failing run was made for this)_

### 2.7. The Report Never Changes a Verdict

- **As an** engineer, **I want** reporting to stay out of the way, **so that** a problem with the report is never mistaken for a problem with the lab.
  - **Acceptance Criteria:**
    - [x] When every check passes but the report cannot be built, then the run's verdict is still "passed" and a visible message says the report is missing and why.  _(verified: run 38173111334 (report build made to fail): run success, Report job failure, annotation `warning | Allure report missing | the report script failed (see the step 'Build the report')`)_
    - [x] When a check fails and the report cannot be built, then the run's verdict is "failed" because of the check, and the existing summary and kept files are still available.  _(verified from its two halves: a failed check makes the run red with the summaries and kept files present (run 38173108099), and a failed Report job cannot change a run's verdict (run 38173111334); the combination was not run as one scenario)_
    - [x] When the engineer compares the report with the run's existing output, then the number of checks in the report equals the number of lab checks the run says it executed, and every check has the same verdict in both.  _(verified: 64 results equal 16 + 48 lab checks counted by the run and by Surefire, every verdict the same (Slice 4 Verify, repeated 2026-10-12))_
    - [x] When the engineer times the documented full local run before and after this change, then the run with reporting is at most 10% slower.  _(verified: 33.589 s against 33.004 s on `main`, medians of three alternating runs: 1.8% slower (Slice 6 Verify))_

### 2.8. Documented

- **As an** engineer new to the project, **I want** the README to say how to get and read the report, **so that** I can use it without asking.
  - **Acceptance Criteria:**
    - [x] When the engineer reads the README, then they find the command that builds the report, where the report is created, and how to open it.  _(verified: README "Visual report": the command `.github/scripts/report.sh`, the path `target/site/allure-maven-plugin/index.html`, "Open it by double-clicking"; followed word for word on a clean checkout (Slice 6 Verify))_
    - [x] When the engineer reads the README's section on automatic runs, then they find where a run's report is kept and how to open a downloaded one.  _(verified: README "Continuous integration": the `allure-report-<browser>-<attempt>` row and "Download `allure-report-…`, unzip it and double-click `index.html`")_
    - [x] When the engineer reads the README, then they find one sentence on what the report shows that the run-page summary does not, and one sentence on what it never contains.  _(verified: README sentences "What it shows that the run-page summary does not" and "What it never contains")_

---

## 3. Scope and Boundaries

### In-Scope

- One visual report covering the workflow checks and the screen checks, built on request after a local run.
- Only the checks that test the lab appear in it; the framework's own self-checks do not.
- Plain-language names, verdicts, durations and labels for every check in the report.
- The list of requests each workflow check made to the lab, with the same details the run log already shows.
- The picture and the recording of a failed screen check inside the report.
- Keeping the one combined report with every automatic run, for passed and failed jobs, as a downloadable file.
- A README section, and updating the project's architecture notes to record the decision.

### Out-of-Scope

- History and trends across runs (how a check behaved last week, flakiness charts). Each report describes one run.
- Publishing the report as a web page reachable by a link; it is a downloadable file on the run page only.
- The framework's own self-checks in the report; they keep their existing console and summary output.
- A report for the lab's own tests; this specification covers the framework's checks only.
- Replacing the run-page summary or the kept result files introduced by spec 006; the report is added beside them.
- Request and answer content in the report; the report shows the same safe request line the run log shows.
- Any change to what the checks test or to the lab.
- Notifications (e-mail, chat) about a run's report.
- A behaviour-driven (Given/When/Then) layer for the checks, which the project deliberately left out.

---

## Change Log

- 2026-10-11 — Slice 1 Verify — The report tool, built through its Maven goal alone, loads a Google Analytics tag into the page and reports each build to Google. The one documented command is therefore a script that switches both off; §2.1 already spoke of "the one documented command", so no criterion changed.
- 2026-10-12 — `/awos:verify` — §2.2, first criterion reworded. It said the first screen shows how many checks passed, failed and were skipped. The report's first screen shows the total, the share that passed and the duration; the counts per verdict are on the list of checks. The owner chose to describe what the report shows.
- 2026-10-12 — owner's request after verification — §2.6 gains three criteria: the run page's summary links straight to the run's report, so a reviewer does not have to look for it among the kept files. Nothing is published outside the run; the link leads to the report kept with that run. Status goes back to In Review until these three are verified.
