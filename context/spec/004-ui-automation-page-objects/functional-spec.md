# Functional Specification: Screen Checks with One Description per Screen (Phase 2)

- **Roadmap Item:** Phase 2 — UI Automation with Page Object Model (Page Objects for Core Screens, Business-Readable UI Scenarios)
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The framework already checks the Mock Lab through its connected interface (specification 003). Nothing yet checks the lab the way a person uses it: through its screens. A lab user signs in, registers a sample, follows it through the workflow and opens the finished report. Right now a mistake on any of those screens would only be caught by someone clicking through by hand.

This specification adds **screen checks**. A screen check is an automated test that drives a real browser through the lab's screens the way a person would, then confirms what appears. It has two parts that only make sense together:

1. **One description per screen.** Each of the lab's five screens (Sign In, Sample List, Create Sample, Sample Details, Report) is described once, in one place, in terms of what a person can see and do on it. When a screen changes, one description changes and every check that uses that screen keeps working.
2. **Checks that read as user workflows.** Each check tells a short story in business language, such as "sign in, register a blood sample for a new patient, see it listed as Accessioned". It does not read as a list of buttons and fields.

In this specification the **engineer** is whoever runs the framework (in practice the author, or a reviewer). The **lab** is the Mock Lab.

The problem today is that screen behaviour is verified only by manual clicking, and a screen-level test suite often turns into fragile, unreadable scripts that break whenever a label moves. The desired outcome is a small set of screen checks that the engineer runs with one command, that a non-developer could read aloud, and that need exactly one edit when a screen changes.

Success is measured by four things:

- Every screen behaviour the project brief's suggested pages cover is exercised by at least one check.
- The engineer can run the screen checks alone, or together with everything else, with one documented command.
- A failing check shows what was expected, what appeared on screen, and a picture of the screen at the moment of failure, with a step-by-step recording of the check.
- The engineer can explain in a couple of minutes why each screen has one description and what belongs in it and what does not.

---

## 2. Functional Requirements (The "What")

### 2.1. Running the Screen Checks

- **As an** engineer, **I want to** run the screen checks with one documented command, **so that** I get a verdict on the lab's screens without knowing how the project is wired.
  - **Acceptance Criteria:**
    - [x] When the engineer runs the documented "screen checks only" command from the project's top-level folder while the lab is running, then only the screen checks run (together with the framework's own quick self-checks, which need no lab), and the overall result is shown at the end.  _(verified 2026-09-28: `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080`: reactor = common, api-client, ui-tests only (no api-tests, no mock-lab-app), common 33 + api-client 218 + ui-tests 82, `BUILD SUCCESS` shown at the end)_
    - [x] When the engineer runs the existing single "run everything" command while the lab is running, then the screen checks run as well as the existing workflow checks, and the overall result covers both.  _(verified: `./mvnw clean test -DbaseUrl=http://localhost:18080`: common 33, api-client 218, ui-tests 82, api-tests 54, one `BUILD SUCCESS` covering both suites)_
    - [x] When the engineer runs the screen checks on a machine that has only the documented required tools, then the browser the checks need is obtained as a documented, one-time setup step, and no other manual installation is needed.  _(verified: `./mvnw -N exec:java` exit 0 (Chromium `chromium-1243` for Playwright 1.63.0 in the Playwright cache), documented in README "Screen checks"; a missing browser fails with a line naming that command (Slice 1 Verify))_
    - [x] When the engineer runs the screen checks with default settings, then no browser window appears on screen; when the engineer runs them with the documented "show the browser" option, then the browser window is visible while the checks run.  _(verified: default runs show no window; `-Dheaded=true -DslowMo=1500` run green while `ps` showed the `ms-playwright/chromium-1243` process with no `--headless` flag)_
    - [x] When the engineer runs the screen checks while the lab is not reachable at the configured address, then the run stops early with a message naming the address it tried, instead of every check failing one by one.  _(verified: lab stopped → `LAB PRE-FLIGHT FAILED: cannot reach the lab at http://localhost:18080. Start it: cd mock-lab-app && ./mvnw spring-boot:run`, `Tests run: 0` in ui-tests)_
    - [x] When the engineer runs the screen checks, then the lab itself is not built, started or stopped by them.  _(verified: `LabLifecycleGuardTest#screenChecksNeverStartOrStopTheLab` green; `git status -- mock-lab-app/` empty at every checkpoint)_

### 2.2. One Description per Screen

- **As a** reviewer (or the author explaining the design), **I want** each lab screen to be described exactly once, **so that** a screen change needs one edit and checks stay readable.
  - **Acceptance Criteria:**
    - [x] When a reader opens the screen-checks part of the project, then they find exactly one description for each of the five screens: Sign In, Sample List, Create Sample, Sample Details and Report.  _(verified: `LoginPage`, `SampleListPage`, `CreateSamplePage`, `SampleDetailsPage`, `ReportPage` in `ui-tests/src/main/java/com/labflow/ui/pages/`)_
    - [x] When a reader opens any screen description, then it offers only what a person can see or do on that screen (for example "enter username", "submit", "read the current status"), and it contains no pass/fail verdicts; the verdicts live only in the checks.  _(verified: `PageObjectVerdictGuardTest` green; Page Objects live in `src/main`, where JUnit and AssertJ are not on the classpath)_
    - [x] When a reader opens any screen check, then it names no on-screen element details (field identifiers, element paths, raw web addresses of screens); everything it touches on screen goes through a screen description.  _(verified: `ScreenBoundaryGuardTest` green (no `locator(`, `getByRole(`, `"#`, `.navigate(`, screen paths in happy/rejection))_
    - [x] When a step in a check moves the user to another screen (for example, submitting valid sign-in details), then the check continues on the description of the screen the user lands on, so the flow between screens is visible in the check itself.  _(verified: navigation methods return the next screen (`signIn` → `SampleListPage`, `createSample` → `SampleDetailsPage`, `openReport` → `ReportPage`, `openExpectingSignIn` → `LoginPage`); checks chain them, e.g. `ReportJourneyTest`)_
    - [x] When the label or position of one element on one screen changes, then exactly one screen description needs updating for all checks to pass again. _(Verified by a deliberate one-element change and revert, as in specification 003.)_  _(verified in Slice 7 Verify (commit c88b178): renaming `#current-status` in the lab template failed 4 checks; editing only `SampleDetailsPage` made them green; both reverted exactly)_

### 2.3. Checks That Read as User Workflows

- **As a** reviewer, **I want** each screen check to read like a short user story, **so that** a non-developer can tell what it proves.
  - **Acceptance Criteria:**
    - [x] When a reader reads the name of any screen check, then it states the user-facing outcome in plain language (for example, "a registered sample appears in the list as Accessioned").  _(verified: e.g. `SampleRegistrationTest` → "A sample registered on screen for a known patient appears in the list as Accessioned")_
    - [x] When a reader reads the body of any screen check, then its steps use lab words (sign in, register a sample, advance the stage, open the report) rather than technical browser actions.  _(verified: check bodies read `signedIn()`, `startNewSample()`, `createSample(…)`, `advance()`, `openReport()`; enforced by `ScreenBoundaryGuardTest`)_
    - [x] When the engineer lists the screen checks in the run output, then each appears under its plain-language name.  _(verified: `TEST-*SignInRejectionTest.xml` lists every check by business name, including the rows "… A blank username is refused" / "… A blank password is refused"; reporters pinned by `PhrasedTestNamesGuardTest`)_

### 2.4. Signing In

- **As a** lab user, **I want** the Sign In screen to let me in only with valid details, **so that** the workflow tools are protected.
  - **Acceptance Criteria:**
    - [x] When the check signs in with the lab's valid user details, then the Sample List screen is shown.  _(verified: `SignInTest#validSignInOpensSampleList` green; see screenshot `004-ui-automation-page-objects-sample-listed-accessioned.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check signs in with a wrong password, then the message "Invalid username or password." is shown and the Sign In screen remains.  _(verified: `SignInRejectionTest#wrongPasswordIsRefused` green; see screenshot `004-ui-automation-page-objects-login-wrong-password.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check submits the Sign In screen with the username or the password left blank, then the message "Username and password are required." is shown. _(Blank username and blank password are each checked, as one check run with different inputs.)_  _(verified: `SignInRejectionTest#blankCredentialsAreRefused` (2 rows) green; see screenshot `004-ui-automation-page-objects-login-blank-fields.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check opens the Sample List screen directly without signing in, then the Sign In screen is shown instead.  _(verified: `SignInRejectionTest#screensRequireSignIn` green; see screenshot `004-ui-automation-page-objects-unauth-redirect-to-sign-in.png` (not kept in the repository since 2026-10-18))_

### 2.5. Registering a Sample

- **As a** lab user, **I want** the Create Sample screen to accept valid samples and explain invalid ones, **so that** only correct samples enter the workflow.
  - **Acceptance Criteria:**
    - [x] Given a patient known to the lab, when the check registers a sample of a supported type for that patient, then the new sample appears on the Sample List screen with that patient and the status "Accessioned".  _(verified: `SampleRegistrationTest#registeredSampleIsListedAsAccessioned` green; see screenshot `004-ui-automation-page-objects-sample-listed-accessioned.png` (not kept in the repository since 2026-10-18) (the lab shows the stage as `ACCESSIONED`, mapped to the Accessioned stage — tech spec §2.3))_
    - [x] When the check submits the Create Sample screen with the patient left blank, then the message "Patient is required." is shown; when it submits with the sample type left blank, then the message "Sample type is required." is shown.  _(verified: `SampleRegistrationRejectionTest#missingFieldIsRequired` (2 rows) green; see screenshot `004-ui-automation-page-objects-create-sample-patient-required.png` (not kept in the repository since 2026-10-18) and screenshot `004-ui-automation-page-objects-create-sample-type-required.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check submits the Create Sample screen with a patient identifier the lab does not know, then the message "Patient not found." is shown.  _(verified: `SampleRegistrationRejectionTest#unknownPatientIsRefused` green; see screenshot `004-ui-automation-page-objects-create-sample-patient-not-found.png` (not kept in the repository since 2026-10-18))_
    - [x] Given a patient known to the lab, when the check submits the Create Sample screen with a sample type the lab does not support, then the message "Invalid sample type." is shown.  _(verified: `SampleRegistrationRejectionTest#unsupportedSampleTypeIsRefused` green; see screenshot `004-ui-automation-page-objects-create-sample-invalid-type.png` (not kept in the repository since 2026-10-18))_

### 2.6. Following a Sample Through the Workflow

- **As a** lab user, **I want** the Sample Details screen to show where a sample is and let me move it on, **so that** I can follow it through the lab.
  - **Acceptance Criteria:**
    - [x] When the check opens the details of a newly registered sample, then the screen shows the status "Accessioned", all eight stages in order, and "Accessioned" as the current stage.  _(verified: `SampleWorkflowTest#newSampleStartsAtAccessioned` green; see screenshot `004-ui-automation-page-objects-details-new-sample-accessioned-current.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check advances a newly registered sample once, then the current status shown becomes "Ready for Processing", "Accessioned" is shown as completed, and the remaining six stages are shown as not yet reached.  _(verified: `SampleWorkflowTest#advancingOnceReachesReadyForProcessing` green; see screenshot `004-ui-automation-page-objects-details-after-one-advance.png` (not kept in the repository since 2026-10-18) (Accessioned completed, Ready for Processing current, six remaining))_
    - [x] When the check advances a sample until it reaches "Report Generated", then the option to advance is no longer shown and the option to view the report is shown.  _(verified: `SampleWorkflowTest#finalStageOffersReportInsteadOfAdvance` green; see screenshot `004-ui-automation-page-objects-details-final-stage-view-report.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check opens the details for a sample identifier that does not exist, then the message "Sample not found." is shown.  _(verified: `SampleDetailsRejectionTest#unknownSampleIsNotFound` green; see screenshot `004-ui-automation-page-objects-details-sample-not-found.png` (not kept in the repository since 2026-10-18))_

### 2.7. Viewing the Report

- **As a** lab user, **I want** to read the finished report for a completed sample, **so that** I can see the outcome.
  - **Acceptance Criteria:**
    - [x] When the check follows one sample all the way from registration to "Report Generated" using only the screens and then opens its report, then the Report screen shows that sample's identifier, a test name, a result value and a generation date. _(This is the one full end-to-end journey through the screens.)_  _(verified: `ReportJourneyTest#fullJourneyThroughScreensEndsInReport` green (exactly 7 advances on screen, report id matches); see screenshot `004-ui-automation-page-objects-report-finished.png` (not kept in the repository since 2026-10-18))_
    - [x] When the check opens the report of a sample that has not reached "Report Generated", then the message "Report is not ready yet." is shown.  _(verified: `ReportRejectionTest#reportNotReadyBeforeFinalStage` green; see screenshot `004-ui-automation-page-objects-report-not-ready.png` (not kept in the repository since 2026-10-18))_

### 2.8. Preparing What a Check Needs

- **As an** engineer, **I want** each screen check to create its own patient and samples, **so that** checks never depend on each other or on leftovers.
  - **Acceptance Criteria:**
    - [x] When any screen check needs a patient, then it creates a new, uniquely named patient for itself before the screen steps begin, through the lab's connected interface, because the lab has no screen for creating patients.  _(verified: `UiTest.newPatient()` registers a `UniqueData`-named patient through `api-client` before any screen step)_
    - [x] When a screen check needs a sample that is already partway through the workflow (for example, the "report not ready" check), then it prepares that sample through the lab's connected interface, and only the behaviour being checked goes through the screens. The one full journey in 2.7 is the exception and uses screens throughout.  _(verified: `SampleWorkflowTest#finalStageOffersReportInsteadOfAdvance` and `ReportRejectionTest` prepare stages with `lab.samples().walkTo(…)`; `ReportJourneyTest` is the only screens-only journey)_
    - [x] When any single screen check is run on its own, or after any other check, then it passes.  _(verified: `-Dtest=SampleRegistrationTest` alone: `Tests run: 1, Failures: 0`; random class and method order pinned by `CheckIndependenceGuardTest`)_
    - [x] When the whole set of screen checks is run twice in a row against the same running lab without restarting it, then both runs pass.  _(verified: two consecutive full ui-tests runs on one lab without restart: 82/0 and 82/0)_

### 2.9. Failures That Explain Themselves

- **As an** engineer, **I want** a failing screen check to show me what went wrong, **so that** I can tell whether the lab or the check is at fault without re-running it.
  - **Acceptance Criteria:**
    - [x] When a screen check fails, then the output names the scenario and states what was expected and what the screen actually showed.  _(verified: deliberate failure in `SampleRegistrationTest`: `[Scenario: A sample registered on screen for a known patient appears in the list as Accessioned] [the status shown in the new sample's row] expected: READY_FOR_PROCESSING but was: ACCESSIONED`; test restored exactly; `ScenarioNameExtensionTest` covers assertion, Playwright and timeout failures)_
    - [x] When a screen check fails, then a picture of the screen at the moment of failure is saved, and its name identifies the scenario it belongs to.  _(verified: exactly one folder `ui-tests/target/ui-failures/SampleRegistrationTest/a-sample-registered-on-screen-…/` with `screenshot.png` + `trace.zip`; copied to screenshot `004-ui-automation-page-objects-failure-artifact-screenshot.png` (not kept in the repository since 2026-10-18); `BrowserExtensionArtifactsTest` proves it automatically)_
    - [x] When a screen check fails, then a step-by-step recording of that check is also saved next to the picture, and when the engineer opens it with the documented viewer, then they can step through each action the check took and see the screen before and after it.  _(verified: `./mvnw -N exec:java -Dexec.args="show-trace …"` started the viewer (`Listening on http://localhost:…`) for that `trace.zip`; the zip holds `trace.trace`, `trace.network`, screencast frames and resources)_
    - [x] When a screen check passes, then no picture or recording is kept for it.  _(verified: after restoring the check, a green run left `ui-tests/target/ui-failures` empty; `BrowserExtensionArtifactsTest` (passing probe keeps nothing))_
    - [x] When a screen check signs in, then the password does not appear in the run output, in the names of saved pictures or recordings, or in the typed values shown inside a recording.  _(verified: `grep -c I_Like_This_Lab` = 0 in console logs, `surefire-reports` and the unzipped failure trace; folder names come only from class and display names (`ArtifactNamesTest`); `SignInTraceTest` proves no trace entry holds the password (actions, DOM snapshots and network log))_

---

## 3. Scope and Boundaries

### In-Scope

- Screen checks for the five lab screens (Sign In, Sample List, Create Sample, Sample Details, Report), covering the successful paths and every on-screen message the lab defines for them.
- Exactly one description per screen; checks that read as user workflows.
- One documented command for screen checks only, inclusion in the existing "run everything" command, a hidden browser by default and a documented option to show it.
- Each check preparing its own uniquely named patient and samples.
- A picture of the screen and a step-by-step recording when a check fails, plus clear expected-versus-actual output.
- A short "how to run the screen checks" addition to the existing README.

### Out-of-Scope

- Running checks in parallel, reusable test-data builders, labels for running subsets of checks (for example "smoke" or "regression"), and choosing a different browser or environment from outside the project. These are the other **Phase 2** roadmap items (Reliable, Isolated Test Execution; Configuration & Logging) and get their own specifications. Screen checks here run one after another, in a single browser type.
- Structured logging, the automated build pipeline, published test reports, the full README and the optional Cucumber demonstration, which are **Phase 3** roadmap items.
- Any change to the lab or its screens. The screens are tested as they are. If a check cannot find something reliably, that is recorded as a finding, not fixed here.
- Checking the "a sample with this ID already exists" message on screen: the lab chooses sample identifiers itself, so a person cannot trigger it from the Create Sample screen (see specification 001).
- Visual appearance checks (colours, layout, pixel comparison), accessibility audits, several screen sizes or mobile layouts, and performance.
- Signing out, session timeouts, multiple users and permission levels: the lab does not offer them.

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- [YYYY-MM-DD] — [source reference] — [what behavior changed and why]
