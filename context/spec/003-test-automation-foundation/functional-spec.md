# Functional Specification: Test Automation Foundation and Lab Workflow Checks (Phase 1)

- **Roadmap Item:** Phase 1 — Maven Multi-Module Project Setup, and Core API Test Automation
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The Mock Lab now exists and can be driven by connected programs (specification 002). What does not exist yet is the thing this whole project is really for: an automated test framework that proves the lab behaves correctly, and that its author can walk a reviewer through.

This specification delivers the first working version of that framework. It has two parts that only make sense together:

1. **A project foundation** — a clearly organised project in which every part has one responsibility, and which knows the lab's core concepts (patient, order, sample, processing job, test result, report) and the eight-stage workflow from "Accessioned" to "Report Generated".
2. **Automated checks of the lab's workflow** — a set of checks that follow the happy-path journey from a new patient to a retrieved report, and a set of checks that prove the lab correctly refuses bad requests. Each check reads as a business scenario, not as a list of technical details.

In this specification a **check** means one automated test, the **engineer** is whoever runs the framework (in practice, the author rehearsing, or a reviewer), and the **lab** is the Mock Lab.

The problem today: nothing verifies the lab automatically, so every claim that "the lab works" rests on manual clicking and one-off scripts. The desired outcome is that the engineer runs one command, gets a clear pass/fail verdict on the lab's workflow rules, and — when something fails — can tell at a glance whether the lab or the check is at fault.

Success is measured by: the engineer can run the whole framework with one documented command on a machine that has only the required tools; every positive and negative scenario the project brief lists is covered by at least one check; a failing check states what was expected and what the lab actually answered; and the engineer can explain the project's structure in a couple of minutes.

---

## 2. Functional Requirements (The "What")

### 2.1. Running Everything with One Command

- **As an** engineer, **I want to** run all the checks with one documented command from the top of the project, **so that** I get a verdict without learning how the project is wired.
  - **Acceptance Criteria:**
    - [ ] When the engineer runs the single documented command from the project's top-level folder while the lab is running, then every check runs and the overall result (passed or failed) is shown at the end.
    - [ ] When the engineer runs the documented command that runs only the lab-workflow checks, then only the checks that use the lab run, together with the framework's own quick self-checks (which need no lab), and nothing else in the project is exercised.
    - [ ] When someone clones the repository onto a machine that has only the documented required tools (a Java runtime, plus internet access the first time) and starts the lab as documented, then the single documented command runs to completion with every check passing and no other manual steps.
    - [ ] When the engineer runs the checks while the lab is running, then the lab itself is not built, started or stopped by the checks.

### 2.2. A Clearly Organised Project

- **As a** reviewer (or the author explaining the design), **I want** each part of the project to have one clear responsibility, **so that** the structure is easy to understand and defend.
  - **Acceptance Criteria:**
    - [ ] When a reader opens the project, then they find exactly these separate parts: one holding the lab's shared concepts, one holding the layer that talks to the lab, and one holding the workflow checks.
    - [ ] When a reader looks for parts reserved for later phases (screen-based checks, reusable test-data helpers), then those parts are not present yet.
    - [ ] When a reader opens any workflow check, then it reads as a sequence of business steps (for example "register a sample", "move it to the next stage") and shows no low-level web-request details.
    - [ ] When a reader looks for anything that talks to the lab, then all of it is found in the layer reserved for that purpose and none of it is inside the checks.

### 2.3. Reaching the Lab

- **As an** engineer, **I want** the checks to find the lab, or tell me clearly why they cannot, **so that** I do not waste time on confusing failures.
  - **Acceptance Criteria:**
    - [ ] When the checks are started while the lab is not running or not yet ready, then they stop immediately without running any workflow check and show a message that names the address they tried and tells the engineer how to start the lab.
    - [ ] When the checks are started and the lab does not accept the configured access key, then they stop immediately without running any workflow check and show a message saying the access key was rejected.
    - [ ] When the engineer supplies a different lab address, or a different access key, from outside the project (without editing any project file), then the checks use those values; the access key is accepted only through the environment, never on the command line.
    - [ ] When the engineer supplies neither, then the checks use the lab's standard local address and its documented local access key.
    - [ ] When the checks run and report their results, then the access key value never appears anywhere in what is shown or in any report file the checks write.

### 2.4. The Lab's Concepts and Stages, Ready to Use

- **As an** author of checks, **I want** the lab's concepts and its eight stages available as shared building blocks, **so that** checks are readable and consistent.
  - **Acceptance Criteria:**
    - [ ] When a check needs the workflow stages, then it can use all eight in their proper order: Accessioned, Ready for Processing, Processing, Sequencing, Bioinformatics, Quality Check, Result Ready, Report Generated.
    - [ ] When a check asks which stage follows "Processing", then it gets "Sequencing"; when it asks which stage follows "Report Generated", then it learns that there is none.
    - [ ] When a check receives a patient, order, sample, progress record, result or report from the lab, then it can read the details it needs (for example a sample's identifier, patient, type and status) directly, without picking apart raw text.

### 2.5. Happy-Path Workflow Checks

- **As an** engineer, **I want** checks that follow the normal journey from a new patient to a finished report, **so that** I know the lab's everyday workflow works.
  - **Acceptance Criteria:**
    - [ ] When a check registers a new patient with a name and then looks that patient up, then it finds the same patient with a lab-assigned identifier and the same name.
    - [ ] When a check places an order for that patient and then looks the order up, then it finds an order for that patient with a creation time.
    - [ ] When a check registers a sample for that patient under that order and then looks the sample up, then it finds a sample with a lab-assigned identifier, the chosen type and the same patient.
    - [ ] When a check has just registered a sample, then the sample's status is "Accessioned".
    - [ ] When a check moves a new sample forward one stage at a time through all seven remaining stages, then every move is accepted, each move shows the new status, and the sample ends at "Report Generated" with its progress listing all eight stages in order.
    - [ ] When a sample has reached "Result Ready", then a check can retrieve its result; and when it has reached "Report Generated", then a check can retrieve its report showing the sample identifier, test name, result value and generation date.
    - [ ] When a single check follows the whole journey (patient, order, sample, all stages, result, report), then every step succeeds and the report belongs to the sample that was registered.

### 2.6. Rejection Checks (What the Lab Must Refuse)

- **As an** engineer, **I want** checks that prove the lab refuses bad requests correctly, **so that** I trust its safeguards as much as its normal behaviour.
  - Every rejection check verifies more than "it was refused": it verifies the kind of rejection, the exact message, that nothing was created or changed (where the lab lets a check observe this — for a refused patient or order, that no new identifier was issued), and the supporting detail the lab sends back where one applies.
  - **Acceptance Criteria:**
    - [ ] When a check registers a patient without a name, a sample without a type, or an order without a patient, then the lab refuses with the matching "… is required." message and nothing is created.
    - [ ] When a check names a patient that does not exist for an order or a sample, then the lab refuses with "Patient not found." and nothing is created.
    - [ ] When a check places an order or registers a sample without naming any patient, then the lab refuses with "Patient is required." and nothing is created.
    - [ ] When a check registers a second sample of the same type under the same order, then the lab refuses with "A sample of this type already exists for this order." and the order still holds only the first sample.
    - [ ] When a check registers a sample with a type the lab does not support, then the lab refuses with "Invalid sample type." and nothing is created.
    - [ ] When a check looks up, moves, or asks for the progress, result or report of a sample that does not exist, then the lab answers "Sample not found." each time.
    - [ ] When a check sends a request without an access key, or with a wrong one, then the lab refuses with "Unauthorized.", the refusal includes the lab's sign-in challenge, and nothing is created or changed.
    - [ ] When a check asks a sample to skip ahead, go backwards, repeat its current stage, or move on from "Report Generated", then the lab refuses with the matching "Invalid status transition: cannot move from … to …." message and the sample's status is unchanged.
    - [ ] When a check sends a request whose content is broken or unreadable, then the lab refuses with "Malformed request body." and nothing is created or changed.
    - [ ] When a check tries an action the item does not support (for example deleting a sample), then the lab refuses with "Method not allowed." and the refusal lists the actions that are allowed.
    - [ ] When a check asks for a sample's result before "Result Ready", or its report before "Report Generated", then the lab answers "Test result is not ready yet." or "Report is not ready yet." respectively.
  - Note: the project brief's own example lists a jump from "Accessioned" straight to "Processing" as valid. The lab only accepts the very next stage (specification 002), so these checks treat that jump as a refusal.

### 2.7. Trustworthy, Repeatable Results

- **As an** engineer, **I want** failures that explain themselves and results that do not depend on leftovers, **so that** I can trust a green run and diagnose a red one.
  - **Acceptance Criteria:**
    - [ ] When a check fails because the lab answered something different from what was expected, then the output names the scenario, states what was expected and what the lab actually answered (including the lab's own message), so the engineer can tell whether the fault is in the lab or in the check.
    - [ ] When any single check is run on its own, or after any other check, then it passes, because each check creates the records it needs instead of relying on another check having run first.
    - [ ] When the whole set of checks is run twice in a row against the same running lab without restarting it, then both runs pass.

---

## 3. Scope and Boundaries

### In-Scope

- A project organised into three clearly separated parts (the lab's shared concepts, the layer that talks to the lab, and the workflow checks), with one documented command to run everything and one to run only the workflow checks.
- The lab's core concepts and its eight-stage workflow available to checks.
- Happy-path checks covering the journey the project brief lists, and rejection checks for every scenario it lists (plus the two "not ready yet" answers).
- Stopping early, with a clear message, when the lab cannot be reached or rejects the configured access key.
- Lab address and access key that can be supplied from outside the project, with the lab's standard local values as defaults.
- Failure output that states what was expected and what actually happened; checks that create their own records and can be repeated.

### Out-of-Scope

- Screen-based checks and page objects, running checks in parallel, reusable test-data builders (each check here simply creates its own uniquely named records), labels for running subsets of checks, and the fuller external configuration (browser, environment) — these belong to **Phase 2** of the roadmap and get their own specifications.
- The automated build pipeline, published test reports, structured logging, the full project README, and the optional Cucumber demonstration — **Phase 3** of the roadmap. (Only the short "how to run the checks" part of the existing README is updated here, because the documented commands this specification requires must live somewhere.)
- Starting or stopping the lab automatically as part of the checks (the engineer starts it, as documented for the lab).
- Any change to the lab itself.
- Performance, load or security testing, and reusing the checks against anything other than the Mock Lab.
- Checking the lab's answers against a formal response schema (the project brief allows this "where appropriate"): the checks verify the specific details that matter — status, business fields, messages and headers — instead.

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- [2026-09-20] — /awos:tech review of this spec — Reworded the "run only the workflow checks" criterion (the framework's own quick self-checks also run); defined "required tools"; the access key is accepted only through the environment and must not appear in report files either (a command-line key leaks into stored reports); split the "unknown patient" / "no patient" refusal criterion, which contradicted itself; clarified what "nothing is created" means for refused patients and orders; noted the minimal README update.
