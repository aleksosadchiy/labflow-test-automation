# Functional Specification: Screen Checks with One Description per Screen (Phase 2)

- **Roadmap Item:** Phase 2 — UI Automation with Page Object Model (Page Objects for Core Screens, Business-Readable UI Scenarios)
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The framework already checks the Mock Lab through its connected interface (specification 003). Nothing yet checks the lab the way a person uses it: through its screens. A lab user signs in, registers a sample, follows it through the workflow and opens the finished report. Right now a mistake on any of those screens would only be caught by someone clicking through by hand.

This specification adds **screen checks**. A screen check is an automated test that drives a real browser through the lab's screens the way a person would, then confirms what appears. It has two parts that only make sense together:

1. **One description per screen.** Each of the lab's five screens (Sign In, Sample List, Create Sample, Sample Details, Report) is described once, in one place, in terms of what a person can see and do on it. When a screen changes, one description changes and every check that uses that screen keeps working.
2. **Checks that read as user workflows.** Each check tells a short story in business language, such as "sign in, register a blood sample for a new patient, see it listed as Accessioned". It does not read as a list of buttons and fields.

In this specification the **engineer** is whoever runs the framework (in practice the author rehearsing, or a reviewer). The **lab** is the Mock Lab.

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
    - [ ] When the engineer runs the documented "screen checks only" command from the project's top-level folder while the lab is running, then only the screen checks run (together with the framework's own quick self-checks, which need no lab), and the overall result is shown at the end.
    - [ ] When the engineer runs the existing single "run everything" command while the lab is running, then the screen checks run as well as the existing workflow checks, and the overall result covers both.
    - [ ] When the engineer runs the screen checks on a machine that has only the documented required tools, then the browser the checks need is obtained as a documented, one-time setup step, and no other manual installation is needed.
    - [ ] When the engineer runs the screen checks with default settings, then no browser window appears on screen; when the engineer runs them with the documented "show the browser" option, then the browser window is visible while the checks run.
    - [ ] When the engineer runs the screen checks while the lab is not reachable at the configured address, then the run stops early with a message naming the address it tried, instead of every check failing one by one.
    - [ ] When the engineer runs the screen checks, then the lab itself is not built, started or stopped by them.

### 2.2. One Description per Screen

- **As a** reviewer (or the author explaining the design), **I want** each lab screen to be described exactly once, **so that** a screen change needs one edit and checks stay readable.
  - **Acceptance Criteria:**
    - [ ] When a reader opens the screen-checks part of the project, then they find exactly one description for each of the five screens: Sign In, Sample List, Create Sample, Sample Details and Report.
    - [ ] When a reader opens any screen description, then it offers only what a person can see or do on that screen (for example "enter username", "submit", "read the current status"), and it contains no pass/fail verdicts; the verdicts live only in the checks.
    - [ ] When a reader opens any screen check, then it names no on-screen element details (field identifiers, element paths, raw web addresses of screens); everything it touches on screen goes through a screen description.
    - [ ] When a step in a check moves the user to another screen (for example, submitting valid sign-in details), then the check continues on the description of the screen the user lands on, so the flow between screens is visible in the check itself.
    - [ ] When the label or position of one element on one screen changes, then exactly one screen description needs updating for all checks to pass again. _(Verified by a deliberate one-element change and revert, as in specification 003.)_

### 2.3. Checks That Read as User Workflows

- **As a** reviewer, **I want** each screen check to read like a short user story, **so that** a non-developer can tell what it proves.
  - **Acceptance Criteria:**
    - [ ] When a reader reads the name of any screen check, then it states the user-facing outcome in plain language (for example, "a registered sample appears in the list as Accessioned").
    - [ ] When a reader reads the body of any screen check, then its steps use lab words (sign in, register a sample, advance the stage, open the report) rather than technical browser actions.
    - [ ] When the engineer lists the screen checks in the run output, then each appears under its plain-language name.

### 2.4. Signing In

- **As a** lab user, **I want** the Sign In screen to let me in only with valid details, **so that** the workflow tools are protected.
  - **Acceptance Criteria:**
    - [ ] When the check signs in with the lab's valid user details, then the Sample List screen is shown.
    - [ ] When the check signs in with a wrong password, then the message "Invalid username or password." is shown and the Sign In screen remains.
    - [ ] When the check submits the Sign In screen with the username or the password left blank, then the message "Username and password are required." is shown. _(Blank username and blank password are each checked, as one check run with different inputs.)_
    - [ ] When the check opens the Sample List screen directly without signing in, then the Sign In screen is shown instead.

### 2.5. Registering a Sample

- **As a** lab user, **I want** the Create Sample screen to accept valid samples and explain invalid ones, **so that** only correct samples enter the workflow.
  - **Acceptance Criteria:**
    - [ ] Given a patient known to the lab, when the check registers a sample of a supported type for that patient, then the new sample appears on the Sample List screen with that patient and the status "Accessioned".
    - [ ] When the check submits the Create Sample screen with the patient left blank, then the message "Patient is required." is shown; when it submits with the sample type left blank, then the message "Sample type is required." is shown.
    - [ ] When the check submits the Create Sample screen with a patient identifier the lab does not know, then the message "Patient not found." is shown.
    - [ ] Given a patient known to the lab, when the check submits the Create Sample screen with a sample type the lab does not support, then the message "Invalid sample type." is shown.

### 2.6. Following a Sample Through the Workflow

- **As a** lab user, **I want** the Sample Details screen to show where a sample is and let me move it on, **so that** I can follow it through the lab.
  - **Acceptance Criteria:**
    - [ ] When the check opens the details of a newly registered sample, then the screen shows the status "Accessioned", all eight stages in order, and "Accessioned" as the current stage.
    - [ ] When the check advances a newly registered sample once, then the current status shown becomes "Ready for Processing", "Accessioned" is shown as completed, and the remaining six stages are shown as not yet reached.
    - [ ] When the check advances a sample until it reaches "Report Generated", then the option to advance is no longer shown and the option to view the report is shown.
    - [ ] When the check opens the details for a sample identifier that does not exist, then the message "Sample not found." is shown.

### 2.7. Viewing the Report

- **As a** lab user, **I want** to read the finished report for a completed sample, **so that** I can see the outcome.
  - **Acceptance Criteria:**
    - [ ] When the check follows one sample all the way from registration to "Report Generated" using only the screens and then opens its report, then the Report screen shows that sample's identifier, a test name, a result value and a generation date. _(This is the one full end-to-end journey through the screens.)_
    - [ ] When the check opens the report of a sample that has not reached "Report Generated", then the message "Report is not ready yet." is shown.

### 2.8. Preparing What a Check Needs

- **As an** engineer, **I want** each screen check to create its own patient and samples, **so that** checks never depend on each other or on leftovers.
  - **Acceptance Criteria:**
    - [ ] When any screen check needs a patient, then it creates a new, uniquely named patient for itself before the screen steps begin, through the lab's connected interface, because the lab has no screen for creating patients.
    - [ ] When a screen check needs a sample that is already partway through the workflow (for example, the "report not ready" check), then it prepares that sample through the lab's connected interface, and only the behaviour being checked goes through the screens. The one full journey in 2.7 is the exception and uses screens throughout.
    - [ ] When any single screen check is run on its own, or after any other check, then it passes.
    - [ ] When the whole set of screen checks is run twice in a row against the same running lab without restarting it, then both runs pass.

### 2.9. Failures That Explain Themselves

- **As an** engineer, **I want** a failing screen check to show me what went wrong, **so that** I can tell whether the lab or the check is at fault without re-running it.
  - **Acceptance Criteria:**
    - [ ] When a screen check fails, then the output names the scenario and states what was expected and what the screen actually showed.
    - [ ] When a screen check fails, then a picture of the screen at the moment of failure is saved, and its name identifies the scenario it belongs to.
    - [ ] When a screen check fails, then a step-by-step recording of that check is also saved next to the picture, and when the engineer opens it with the documented viewer, then they can step through each action the check took and see the screen before and after it.
    - [ ] When a screen check passes, then no picture or recording is kept for it.
    - [ ] When a screen check signs in, then the password does not appear in the run output, in the names of saved pictures or recordings, or in the typed values shown inside a recording.

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
