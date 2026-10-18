# Functional Specification: Test Automation Foundation and Lab Workflow Checks (Phase 1)

- **Roadmap Item:** Phase 1 — Maven Multi-Module Project Setup, and Core API Test Automation
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The Mock Lab now exists and can be driven by connected programs (specification 002). What does not exist yet is the thing this whole project is really for: an automated test framework that proves the lab behaves correctly, and that its author can walk a reviewer through.

This specification delivers the first working version of that framework. It has two parts that only make sense together:

1. **A project foundation** — a clearly organised project in which every part has one responsibility, and which knows the lab's core concepts (patient, order, sample, processing job, test result, report) and the eight-stage workflow from "Accessioned" to "Report Generated".
2. **Automated checks of the lab's workflow** — a set of checks that follow the happy-path journey from a new patient to a retrieved report, and a set of checks that prove the lab correctly refuses bad requests. Each check reads as a business scenario, not as a list of technical details.

In this specification a **check** means one automated test, the **engineer** is whoever runs the framework (in practice, the author, or a reviewer), and the **lab** is the Mock Lab.

The problem today: nothing verifies the lab automatically, so every claim that "the lab works" rests on manual clicking and one-off scripts. The desired outcome is that the engineer runs one command, gets a clear pass/fail verdict on the lab's workflow rules, and — when something fails — can tell at a glance whether the lab or the check is at fault.

Success is measured by: the engineer can run the whole framework with one documented command on a machine that has only the required tools; every positive and negative scenario the project brief lists is covered by at least one check; a failing check states what was expected and what the lab actually answered; and the engineer can explain the project's structure in a couple of minutes.

---

## 2. Functional Requirements (The "What")

### 2.1. Running Everything with One Command

- **As an** engineer, **I want to** run all the checks with one documented command from the top of the project, **so that** I get a verdict without learning how the project is wired.
  - **Acceptance Criteria:**
    - [x] When the engineer runs the single documented command from the project's top-level folder while the lab is running, then every check runs and the overall result (passed or failed) is shown at the end.  _(verified: `./mvnw clean test -DbaseUrl=http://localhost:18080` with the lab running: common 33 + api-client 218 + api-tests 54 tests, `BUILD SUCCESS`, exit 0, overall result at the end; the same lab answered a second full run green)_
    - [x] When the engineer runs the documented command that runs only the lab-workflow checks, then only the checks that use the lab run, together with the framework's own quick self-checks (which need no lab), and nothing else in the project is exercised.  _(verified: `./mvnw -pl api-tests -am test`: reactor = common, api-client, api-tests only (0 mentions of `mock-lab-app` in the output), 33 + 218 + 54 tests, `BUILD SUCCESS`)_
    - [x] When someone clones the repository onto a machine that has only the documented required tools (a Java runtime, plus internet access the first time) and starts the lab as documented, then the single documented command runs to completion with every check passing and no other manual steps.  _(verified: fresh `git clone` of d2c5d7d with an empty local Maven repository (211 artifacts downloaded): 33 / 218 / 54 green, exit 0, no other step. Caveat: the wrapper's own Maven distribution came from the local cache, and only JDK 26 with `--release 17` was available, not a real JDK 17)_
    - [x] When the engineer runs the checks while the lab is running, then the lab itself is not built, started or stopped by the checks.  _(verified: java process list identical before and after all runs; `mock-lab-app/target` (230 files) identical in size and mtime; `FrameworkBoundaryGuardTest#frameworkNeverStartsOrStopsTheLab` fails if the framework ever starts a process)_

### 2.2. A Clearly Organised Project

- **As a** reviewer (or the author explaining the design), **I want** each part of the project to have one clear responsibility, **so that** the structure is easy to understand and defend.
  - **Acceptance Criteria:**
    - [x] When a reader opens the project, then they find exactly these separate parts: one holding the lab's shared concepts, one holding the layer that talks to the lab, and one holding the workflow checks.  _(verified: root `pom.xml` `<modules>` = `common`, `api-client`, `api-tests` (the lab stays outside the reactor))_
    - [x] When a reader looks for parts reserved for later phases (screen-based checks, reusable test-data helpers), then those parts are not present yet.  _(verified: no `ui-tests` or `test-data` directory exists; `git ls-files ui-tests test-data` = 0)_
    - [x] When a reader opens any workflow check, then it reads as a sequence of business steps (for example "register a sample", "move it to the next stage") and shows no low-level web-request details.  _(verified: `NoRawHttpInChecksTest` and `FrameworkBoundaryGuardTest#checksShowNoLowLevelWebRequestDetails` fail on any request library, JDK web class or URL path in a check; checks read as steps (e.g. `FullJourneyTest`, `InvalidTransitionRejectionTest`))_
    - [x] When a reader looks for anything that talks to the lab, then all of it is found in the layer reserved for that purpose and none of it is inside the checks.  _(verified: `git grep io.restassured` finds it only in `api-client` (8 files), none in `api-tests`; both guards above enforce it)_

### 2.3. Reaching the Lab

- **As an** engineer, **I want** the checks to find the lab, or tell me clearly why they cannot, **so that** I do not waste time on confusing failures.
  - **Acceptance Criteria:**
    - [x] When the checks are started while the lab is not running or not yet ready, then they stop immediately without running any workflow check and show a message that names the address they tried and tells the engineer how to start the lab.  _(verified: no lab (`-DbaseUrl=http://localhost:18999`): `LAB PRE-FLIGHT FAILED: cannot reach the lab at http://localhost:18999. Start it: cd mock-lab-app && ./mvnw spring-boot:run`, api-tests `Tests run: 0`, exit 1; `LabPreflightTest`, `LabRunWiringTest#labPreflightIsRegisteredForEveryRun`)_
    - [x] When the checks are started and the lab does not accept the configured access key, then they stop immediately without running any workflow check and show a message saying the access key was rejected.  _(verified: `API_TOKEN=wrong-key-for-test ./mvnw -B test -pl api-tests -am -DbaseUrl=http://localhost:18080`: `LAB PRE-FLIGHT FAILED: the lab at http://localhost:18080 rejected the access key (set the API_TOKEN environment variable)`, `Tests run: 0`, exit 1; `LabPreflightTest#rejectedKeyFailsNamingApiTokenAndNotTheKey`)_
    - [x] When the engineer supplies a different lab address, or a different access key, from outside the project (without editing any project file), then the checks use those values; the access key is accepted only through the environment, never on the command line.  _(verified: `BASE_URL=http://localhost:18080` with no `-D` → run green; `-DapiToken=bogus-cli-key-zzz` ignored (run still green on the default key); `LabConfigTest#apiTokenSystemPropertyIsIgnored` and the precedence tests)_
    - [x] When the engineer supplies neither, then the checks use the lab's standard local address and its documented local access key.  _(verified: `LabConfigTest#defaultValuesAreTheLabsLocalOnes` (`http://localhost:8080`, the documented local key); nothing on 8080 was touched)_
    - [x] When the checks run and report their results, then the access key value never appears anywhere in what is shown or in any report file the checks write.  _(verified: the default key and the wrong key appear in no console log and in no non-class file under `*/target` (grep exit 1); `SecretRedactionTest`, `LabConfigTest#labConfigToStringNeverContainsTheKey`. Documented caveat: a key passed as `-DapiToken` does land in Surefire XML, which is why the key is accepted only through the environment)_

### 2.4. The Lab's Concepts and Stages, Ready to Use

- **As an** author of checks, **I want** the lab's concepts and its eight stages available as shared building blocks, **so that** checks are readable and consistent.
  - **Acceptance Criteria:**
    - [x] When a check needs the workflow stages, then it can use all eight in their proper order: Accessioned, Ready for Processing, Processing, Sequencing, Bioinformatics, Quality Check, Result Ready, Report Generated.  _(verified: `WorkflowStageTest#thereAreExactlyEightStages`, `#declarationOrderIsTheWorkflowOrder`, `#displayNamesAreExact`)_
    - [x] When a check asks which stage follows "Processing", then it gets "Sequencing"; when it asks which stage follows "Report Generated", then it learns that there is none.  _(verified: `WorkflowStageTest#stageAfterProcessingIsSequencing`, `#thereIsNoStageAfterReportGenerated`, `#everyStageIsFollowedByTheNextConstant`)_
    - [x] When a check receives a patient, order, sample, progress record, result or report from the lab, then it can read the details it needs (for example a sample's identifier, patient, type and status) directly, without picking apart raw text.  _(verified: `LabJsonMappingTest` (patient, order, sample, progress, result, report, error), `PatientServiceTest`/`SampleServiceTest`, and the happy checks reading fields directly)_

### 2.5. Happy-Path Workflow Checks

- **As an** engineer, **I want** checks that follow the normal journey from a new patient to a finished report, **so that** I know the lab's everyday workflow works.
  - **Acceptance Criteria:**
    - [x] When a check registers a new patient with a name and then looks that patient up, then it finds the same patient with a lab-assigned identifier and the same name.  _(verified: `PatientOrderSampleTest#registeredPatientCanBeLookedUp`)_
    - [x] When a check places an order for that patient and then looks the order up, then it finds an order for that patient with a creation time.  _(verified: `PatientOrderSampleTest#placedOrderCanBeLookedUp`)_
    - [x] When a check registers a sample for that patient under that order and then looks the sample up, then it finds a sample with a lab-assigned identifier, the chosen type and the same patient.  _(verified: `PatientOrderSampleTest#registeredSampleCanBeLookedUp`)_
    - [x] When a check has just registered a sample, then the sample's status is "Accessioned".  _(verified: `PatientOrderSampleTest#newSampleIsAccessioned`)_
    - [x] When a check moves a new sample forward one stage at a time through all seven remaining stages, then every move is accepted, each move shows the new status, and the sample ends at "Report Generated" with its progress listing all eight stages in order.  _(verified: `SampleProgressionTest#movesThroughAllSevenStagesOneAtATime`)_
    - [x] When a sample has reached "Result Ready", then a check can retrieve its result; and when it has reached "Report Generated", then a check can retrieve its report showing the sample identifier, test name, result value and generation date.  _(verified: `SampleProgressionTest#resultAvailableAtResultReady`, `#reportAvailableAtReportGenerated`)_
    - [x] When a single check follows the whole journey (patient, order, sample, all stages, result, report), then every step succeeds and the report belongs to the sample that was registered.  _(verified: `FullJourneyTest#wholeJourneyFromPatientToReport`)_

### 2.6. Rejection Checks (What the Lab Must Refuse)

- **As an** engineer, **I want** checks that prove the lab refuses bad requests correctly, **so that** I trust its safeguards as much as its normal behaviour.
  - Every rejection check verifies more than "it was refused": it verifies the kind of rejection, the exact message, that nothing was created or changed (where the lab lets a check observe this — for a refused patient or order, that no new identifier was issued), and the supporting detail the lab sends back where one applies.
  - **Acceptance Criteria:**
    - [x] When a check registers a patient without a name, a sample without a type, or an order without a patient, then the lab refuses with the matching "… is required." message and nothing is created.  _(verified: `InputValidationRejectionTest#patientWithoutNameIsRefused` (missing and blank), `#sampleWithoutTypeIsRefused`, `#orderWithoutPatientIsRefused`: kind, exact message, no `Location`, no sample created)_
    - [x] When a check names a patient that does not exist for an order or a sample, then the lab refuses with "Patient not found." and nothing is created.  _(verified: `InputValidationRejectionTest#orderForUnknownPatientIsRefused`, `#sampleForUnknownPatientIsRefused` (404 "Patient not found.", nothing created))_
    - [x] When a check places an order or registers a sample without naming any patient, then the lab refuses with "Patient is required." and nothing is created.  _(verified: `InputValidationRejectionTest#sampleWithoutPatientIsRefused`, `#orderWithoutPatientIsRefused` (400 "Patient is required."))_
    - [x] When a check registers a second sample of the same type under the same order, then the lab refuses with "A sample of this type already exists for this order." and the order still holds only the first sample.  _(verified: `DuplicateSampleRejectionTest#secondSampleOfSameTypeRefused` (409; the order and the patient still hold only the first sample) and `#sampleOfOtherTypeAccepted`)_
    - [x] When a check registers a sample with a type the lab does not support, then the lab refuses with "Invalid sample type." and nothing is created.  _(verified: `InputValidationRejectionTest#unsupportedSampleTypeIsRefused` (PLASMA → 400 "Invalid sample type.", nothing created))_
    - [x] When a check looks up, moves, or asks for the progress, result or report of a sample that does not exist, then the lab answers "Sample not found." each time.  _(verified: `UnknownSampleRejectionTest#unknownSampleIsRefused` (get, move, progress, result, report: 5 separate results, 404 "Sample not found."))_
    - [x] When a check sends a request without an access key, or with a wrong one, then the lab refuses with "Unauthorized.", the refusal includes the lab's sign-in challenge, and nothing is created or changed.  _(verified: `AuthenticationRejectionTest#requestWithoutTheRightKeyIsRefused` (no key / wrong key × register patient, register sample, move: 6 results, 401 "Unauthorized.", `WWW-Authenticate: Bearer`, nothing changed) and `#readWithoutKeyIsRefused`)_
    - [x] When a check asks a sample to skip ahead, go backwards, repeat its current stage, or move on from "Report Generated", then the lab refuses with the matching "Invalid status transition: cannot move from … to …." message and the sample's status is unchanged.  _(verified: `InvalidTransitionRejectionTest#wrongMoveIsRefused` (skip ahead, backwards, repeat, from Report Generated: 4 results, exact message, stage unchanged))_
    - [x] When a check sends a request whose content is broken or unreadable, then the lab refuses with "Malformed request body." and nothing is created or changed.  _(verified: `BadRequestRejectionTest#malformedBodyRefused` (truncated JSON, plain text, empty), `#malformedPatientBodyRefused`: 400 "Malformed request body.", nothing created)_
    - [x] When a check tries an action the item does not support (for example deleting a sample), then the lab refuses with "Method not allowed." and the refusal lists the actions that are allowed.  _(verified: `BadRequestRejectionTest#deleteSampleIsMethodNotAllowed`: 405 "Method not allowed.", `Allow: GET`, the sample is still there)_
    - [x] When a check asks for a sample's result before "Result Ready", or its report before "Report Generated", then the lab answers "Test result is not ready yet." or "Report is not ready yet." respectively.  _(verified: `NotReadyRejectionTest#requestedTooEarlyIsRefused` (result before Result Ready, report before Report Generated, report at Result Ready: 4 results, 409))_
  - Note: the project brief's own example lists a jump from "Accessioned" straight to "Processing" as valid. The lab only accepts the very next stage (specification 002), so these checks treat that jump as a refusal.

### 2.7. Trustworthy, Repeatable Results

- **As an** engineer, **I want** failures that explain themselves and results that do not depend on leftovers, **so that** I can trust a green run and diagnose a red one.
  - **Acceptance Criteria:**
    - [x] When a check fails because the lab answered something different from what was expected, then the output names the scenario, states what was expected and what the lab actually answered (including the lab's own message), so the engineer can tell whether the fault is in the lab or in the check.  _(verified: deliberate failure (one expected message changed in `DuplicateSampleRejectionTest`): `[Scenario: A second blood sample under the same order is refused, …] [POST /api/samples -> 409 "A sample of this type already exists for this order."] expected rejection CONFLICT "Patient not found." but the lab said "A sample of this type already exists for this order."`; file restored exactly, re-run green; `LabAssertionsTest`, `ScenarioNameExtensionTest`)_
    - [x] When any single check is run on its own, or after any other check, then it passes, because each check creates the records it needs instead of relying on another check having run first.  _(verified: single checks alone, all exit 0: `PatientOrderSampleTest#registeredPatientCanBeLookedUp`, `AuthenticationRejectionTest#readWithoutKeyIsRefused`, `DuplicateSampleRejectionTest#sampleOfOtherTypeAccepted`; class order differed between two runs; `CheckIndependenceGuardTest#checksRunInRandomOrder`)_
    - [x] When the whole set of checks is run twice in a row against the same running lab without restarting it, then both runs pass.  _(verified: the whole set ran green repeatedly on one lab without restarting it (runs A, B, E, F all 33 / 218 / 54); `CheckIndependenceGuardTest#uniqueDataIsDifferentEachTime`)_

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
