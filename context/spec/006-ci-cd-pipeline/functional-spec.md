# Functional Specification: Automatic Check Runs on Every Change, with Results You Can Investigate (Phase 3)

- **Roadmap Item:** Phase 3 — CI/CD Pipeline (GitHub Actions Workflow; Test Reporting in CI). Also closes the Phase 0 leftovers "Runnable Locally & in CI" and "Stable Test Environment".
- **Status:** In Review
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The framework can check the Mock Lab in two ways: **workflow checks** through the lab's connected interface, and **screen checks** through its screens in a browser. Today both run only when someone starts them by hand on their own machine, with the lab started by hand first. A change that breaks a check can therefore be merged without anyone noticing, and a reviewer has to trust "it passed on my machine".

This specification adds an **automatic check run**. Every time a change is shared to the project's online repository, the project's hosting service builds the project, starts its own copy of the lab, runs the checks, and shows the result next to the change. Workflow checks and screen checks run as two **separate check jobs**, so one family can fail, be re-run, or be started on its own without the other. When a check fails, everything needed to understand why (the results report, the run log, and for screen checks the picture and step-by-step recording) is kept with the run, so the engineer can investigate from the run page alone.

In this specification the **engineer** is whoever pushes changes or reviews them (in practice the author, or a reviewer looking at the repository). The **run page** is the page the hosting service shows for one automatic run. The **lab** is the Mock Lab.

Success is measured by four things:

- Every shared change gets a pass/fail verdict without anyone running anything by hand.
- Workflow checks and screen checks appear as two separate results that can each be re-run or started on their own.
- A failed run can be understood from its run page and kept files alone, without re-running it locally.
- The same checks that pass locally pass in the automatic run, repeatedly, with no flaky failures caused by the environment.

---

## 2. Functional Requirements (The "What")

### 2.1. Automatic Runs on Every Change

- **As an** engineer, **I want** the checks to run automatically whenever a change is shared, **so that** a broken change is caught before it is merged.
  - **Acceptance Criteria:**
    - [x] When the engineer shares a change on any branch of the online repository, then an automatic run starts without any manual step and its verdict appears next to that change.  _(verified 2026-10-05: every push to spec/ci-cd-pipeline and to ci-probe/* branches started CI on its own; check run on commit 3a68b89 → `Build completed success` (run 37290667741))_
    - [x] When the engineer opens a request to merge a branch into the main line, then the automatic run's verdict appears on that merge request.  _(verified: draft PR #7 — `gh pr checks 7` lists Build / Workflow checks / Screen checks pass for pull_request runs (e.g. 37333301499))_
    - [x] When the engineer starts a run by hand from the hosting service, then they can choose to run only workflow checks, only screen checks, or both, and which browser the screen checks use (Chromium or WebKit); a hand-started run always runs the regression set.  _(verified: `gh workflow run CI -f family=ui -f browser=webkit` → run 37325826001: Build success, Screen checks (webkit) success, Workflow checks skipped; `-f family=api` → run 37325165002: Screen checks skipped)_
    - [x] When a run is started and nothing else is chosen, then it runs every regression check of both families in Chromium; automatic runs on every change never run a smaller subset, because the whole suite takes under a minute.  _(verified: push runs run both families in Chromium with `-Dgroups="regression | !(api | ui)"` (banner `browser=chromium`, `Lab checks run: 48` / `16`); pinned by `CiWorkflowGuardTest`)_
    - [x] When the engineer shares two changes to the same branch in quick succession, then both runs complete and each change gets its own verdict.  _(verified: commits 416db59 (run 37332649293, until 15:27:28) and cbf5152 (run 37332912900, created 15:27:00) overlapped and both completed success; no `concurrency` block (`CiWorkflowGuardTest`))_

### 2.2. Separate Jobs for Workflow Checks and Screen Checks

- **As an** engineer, **I want** workflow checks and screen checks to run as two separate jobs, **so that** I can see, re-run and investigate each family on its own.
  - **Acceptance Criteria:**
    - [x] When a run starts, then the run page shows a build step and two separate check jobs named so a reader can tell them apart (for example "Workflow checks" and "Screen checks").  _(verified: run page shows jobs `Build`, `Workflow checks`, `Screen checks` (run 37332912900))_
    - [x] When the screen-check job fails and the workflow-check job passes, then the run page shows one job failed and one passed, and the overall run is marked failed.  _(verified: probe run 37320300679 → Build success, Workflow checks success, Screen checks failure, run conclusion failure)_
    - [x] When the engineer re-runs only the failed job from the run page, then only that family of checks runs again.  _(verified: run 37327130875 attempt 2 after `gh run rerun --failed` → only Screen checks has a new start time (14:47:09); Build and Workflow checks keep attempt-1 times)_
    - [x] When the build itself fails (the project does not compile or its quick self-checks fail), then neither check job starts and the run page shows the build as the failed step.  _(verified: probe run 37327109795 (compile error) → Build failure, Workflow checks skipped, Screen checks skipped)_

### 2.3. Each Job Brings Its Own Lab

- **As an** engineer, **I want** every check job to start its own fresh lab and wait until it is ready, **so that** results never depend on a shared or half-started lab.
  - **Acceptance Criteria:**
    - [x] When a check job starts, then it starts its own copy of the lab and the checks begin only after the lab reports it is ready.  _(verified: run 37332912900 → `Lab ready on :8080 after 4s` before the test step in both check jobs)_
    - [x] When the lab fails to become ready within 60 seconds, then the job stops with a message saying the lab did not start, and the lab's own start-up output is kept with the run.  _(verified: probe run 37291651992 annotation `Lab did not start: Mock Lab not ready on :8080 within 1 s — see mock-lab-app/target/lab-startup.log`, log tail printed; `lab-startup.log` is in every results artifact)_
    - [x] When a check job finishes, whether it passed or failed, then the lab it started is no longer running.  _(verified: `Lab stopped` at the end of both check jobs (run 37332912900); the stop step is `if: always()` and ran on the red probes too)_
    - [x] When a run uses settings, then it uses the "ci" environment, and the access key and sign-in details come from the hosting service's protected settings or the lab's documented local defaults, never written into the run's visible configuration: the access key comes from the repository's protected settings, and when none is set the lab's documented local key is used.  _(verified: banner `environment=ci … key=***`; step `Access key: the lab default from application.yml` (no secret) and `the API_TOKEN repository secret` (temporary secret, run 37325825328 attempt 2); `mock-lab-local-token` appears 0 times in the log)_

### 2.4. Results Visible on the Run Page

- **As a** reviewer, **I want** a readable summary of each job's results on the run page, **so that** I can judge a change without downloading anything.
  - **Acceptance Criteria:**
    - [x] When a check job finishes, then the run page shows a summary for that job with the number of checks passed, failed and skipped, and how long the job took.  _(verified: `✅ **Workflow checks**: 81 passed, 0 skipped in 47s`, `✅ **Screen checks (chromium)**: 109 passed, 0 skipped in 1m39s` (run 37332912900))_
    - [x] When a check fails, then the summary names the failing check by its plain-language name and shows its failure message (expected versus actual).  _(verified: probe run 37320300679 → `| Signing in with the lab's valid details opens the Sample List screen | SignInTest | Expecting value to be false but was true |`)_
    - [x] When every check passes, then the summary says so in one line per family, without listing every check.  _(verified: one line per family and per Build module on green run 37332912900)_
    - [x] When a run starts, then its log shows the run banner with the lab address, environment and browser, and the access key only as hidden.  _(verified: `Lab run: environment=ci address=http://localhost:8080 key=*** browser=chromium` (run 37332912900))_

### 2.5. Failure Details Kept with the Run

- **As an** engineer investigating a failure, **I want** the run's reports and logs kept with it, **so that** I can investigate without reproducing the failure locally.
  - **Acceptance Criteria:**
    - [x] When a check job finishes, whether it passed or failed, then its results report and its run log can be downloaded from the run page.  _(verified: artifacts `workflow-checks-results-1` and `screen-checks-results-chromium-1` on green run 37332912900 and red run 37321721439, each with surefire reports, the run log and `lab-startup.log`)_
    - [x] When a screen check fails, then the picture of the screen at the moment of failure and its step-by-step recording can be downloaded from the run page, and the recording opens in the documented viewer.  _(verified: red run 37321721439 artifact `screen-failures-chromium-1` → one `screenshot.png` + `trace.zip` for SignInTest; the trace unzips and opens with show-trace)_
    - [x] When every screen check passes, then no failure pictures or recordings are kept for that run.  _(verified: green run 37332912900 has no `screen-failures-*` artifact)_
    - [x] When anyone downloads the kept files, then they contain no access key or sign-in password, the reports and run logs contain no patient name, and any patient name visible in a screen check's failure picture or recording is one of the framework's obviously made-up names (starting with "TEST-").  _(verified: downloaded all artifacts of runs 37321721439 and 37332912900 (234 files, traces unzipped): key 0, password 0, `TEST-patient-` 0 outside screen-failures evidence (FS Change Log 2026-10-05))_
    - [x] When the engineer opens a run up to 90 days old (the hosting service's standard limit), then its kept files can still be downloaded.  _(verified: artifacts created 2026-10-05 expire 2027-01-03 (90 days), `expired=false`)_

### 2.6. A Stable, Repeatable Environment

- **As an** engineer, **I want** the automatic run to be as reliable as a local run, **so that** a red result always means a real problem.
  - **Acceptance Criteria:**
    - [x] When the same unchanged branch is run three times in a row, then all three runs pass.  _(verified: run 37330042574 attempts 1–3 all success on bc910f1 (3m02s, 2m26s, 2m47s))_
    - [x] When a run is started, then the tools and browser it needs are prepared automatically, with no manual step on the hosting service, and repeat runs reuse what was already prepared so they start faster than the first.  _(verified: no manual steps; Maven cache hit (275 MiB incl. the lab's dependencies) and Playwright cache hit `playwright-Linux-1.63.0-chromium`; re-run Screen checks 102 s vs 114 s first run)_
    - [x] When a run is started, then the whole run (build and both check jobs) finishes within 10 minutes, and any single job that has not finished after 15 minutes is stopped with a timeout.  _(verified: runs take ~2.5–3 min in total; `timeout-minutes: 15` on all three jobs (`CiWorkflowGuardTest`))_

### 2.7. Documented

- **As a** reviewer, **I want** the README to explain the automatic run, **so that** I know what it does and how to use it.
  - **Acceptance Criteria:**
    - [x] When a reader opens the README, then they find a short section explaining when automatic runs start, the two check jobs, how to start one by hand, where to find the summary and the kept files, and how to open a failure recording.  _(verified: README `## Continuous integration` (line 248): triggers, the three jobs, Run workflow / `gh workflow run`, summary, artifacts, `show-trace`, `API_TOKEN` secret, branch protection)_
    - [ ] When a reader opens the README, then a status badge shows whether the latest run on the main line passed.

---

## 3. Scope and Boundaries

### In-Scope

- An automatic run on every shared change and on merge requests, plus a hand-started run.
- A build step and two separate check jobs (workflow checks, screen checks), each starting and stopping its own lab.
- Results summaries on the run page; downloadable reports, run logs and screen-check failure pictures and recordings.
- Automatic preparation and reuse of tools and the browser; time limits.
- A short README section and a status badge.
- Closing the Phase 0 items "Runnable Locally & in CI" and "Stable Test Environment".

### Out-of-Scope

- Deploying the lab or the framework anywhere; the automatic run only tests.
- Running screen checks in several browsers on every change (a hand-started run may choose the browser).
- Branch-protection rules that block merging on a red run: these are repository settings the owner switches on by hand. The README may describe how.
- Richer report formats or dashboards beyond the run-page summary (for example Allure).
- The full README rewrite, clean history and project walkthrough (**Phase 3 — Documentation & Review Readiness**); only the automatic-run section and badge are added here.
- The optional Cucumber demonstration (**Phase 3 — Optional BDD Layer**).
- Any change to the lab or to the checks themselves, except where the automatic run reveals an environment problem that must be fixed for stability (recorded as a finding).

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- 2026-10-05 — Slice 5 Verify finding — a failed screen check's picture and recording show the screen as it was, so a Sample List screen shows the made-up names of patients created by checks running alongside (6 `TEST-patient-…` names found in one recording). Decision: accepted and documented rather than masked. Screen evidence is only useful if it shows the screen; the framework only ever creates obviously fake `TEST-` patients; the access key and password stay out of every kept file. A real regulated system would keep such evidence access-restricted or mask the patient column (noted in the README and architecture).
