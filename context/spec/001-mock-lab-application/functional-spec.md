# Functional Specification: Mock Lab Application (System Under Test)

- **Roadmap Item:** Phase 0 — Simulated Lab Backend (API Target) & UI Target Application
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

There is no real laboratory system to point the test automation framework at — this project simulates one instead of reproducing a real vendor's product. Without something to automate against, none of the later API or UI test work can begin.

The Mock Lab Application is a small, deliberately minimal stand-in for that real system. A person (or the automated tests acting like one) signs in, registers a new laboratory sample for a patient, watches it move through a simplified processing workflow, and views the finished report once it's ready. It doesn't need to look production-ready or contain any real laboratory science — it only needs to behave consistently enough that automated tests can rely on it, including when things go wrong (missing information, invalid input, samples that don't exist).

Success is measured by whether this application reliably supports both the REST API tests and the UI tests planned for later phases — including their failure-path scenarios — every time it's started, locally or in CI.

---

## 2. Functional Requirements (The "What")

### 2.1. Sign In

- **As a** lab-workflow user, **I want to** sign in with a username and password, **so that** I can access the sample workflow tools.
  - **Acceptance Criteria:**
    - When the user enters valid credentials and submits the sign-in form, then they are taken to the Sample List screen.
    - When the user enters invalid credentials and submits the sign-in form, then they see the message "Invalid username or password." and remain on the Sign In screen.
    - When the user submits the sign-in form with the username or password left blank, then they see the message "Username and password are required."
    - When someone who is not signed in tries to open any other screen directly, then they are redirected to the Sign In screen.

### 2.2. View Sample List

- **As a** lab-workflow user, **I want to** see every registered sample and its current status, **so that** I can track where each one is in the workflow.
  - **Acceptance Criteria:**
    - When the signed-in user opens the Sample List screen, then they see every registered sample with its identifier, associated patient, and current status.
    - When the signed-in user opens the Sample List screen and no samples are registered yet, then they see the message "No samples registered yet."
    - The list shows every registered sample with no search, filtering, or pagination — the test dataset is always small enough that a flat list is sufficient.

### 2.3. Register a New Sample

- **As a** lab-workflow user, **I want to** register a new sample for a patient, **so that** it enters the lab workflow and can be tracked.
  - **Acceptance Criteria:**
    - The Create Sample form requires exactly two fields: the patient and the sample type. No other field is required to register a sample.
    - When the user fills in a patient and a sample type on the Create Sample form and submits it, then a new sample appears in the Sample List with the status "Accessioned."
    - When the user submits the Create Sample form with the patient or sample type left blank, then they see a message telling them that field is required (e.g. "Patient is required." or "Sample type is required.").
    - When the user submits the Create Sample form with a patient identifier that doesn't match any known patient, then they see the message "Patient not found."
    - When the user submits the Create Sample form with a sample type that isn't one of the supported types, then they see the message "Invalid sample type."
    - When the user submits the Create Sample form with a sample identifier that is already in use, then they see the message "A sample with this ID already exists."

### 2.4. View Sample Details & Workflow Progress

- **As a** lab-workflow user, **I want to** open a sample and see its full status history, **so that** I know exactly where it stands in the workflow.
  - **Acceptance Criteria:**
    - When the user opens a sample's details, then they see its current status and the full sequence of stages a sample passes through (Accessioned → Ready for Processing → Processing → Sequencing → Bioinformatics → Quality Check → Result Ready → Report Generated), with the completed and remaining stages visually distinguished.
    - When the user opens the details for a sample identifier that doesn't exist, then they see the message "Sample not found."
    - When the user is viewing a sample that isn't yet at its final stage, then they see a button to advance it to the next stage in the workflow.
    - When the user clicks the button to advance a sample to its next stage, then the sample's status updates immediately and the newly reached stage is shown as current.
    - When the user is viewing a sample that has reached its final stage ("Report Generated"), then no advance-stage button is shown.

### 2.5. View Report

- **As a** lab-workflow user, **I want to** view the finished report once a sample's processing is complete, **so that** I can see the outcome.
  - **Acceptance Criteria:**
    - When the user opens the details for a sample whose status is "Report Generated," then they see an option to view its report.
    - When the user opens the report for a completed sample, then they see the sample identifier, the test name, the result value, and the date the report was generated.
    - When the user tries to view the report for a sample that hasn't reached "Report Generated" yet, then they see the message "Report is not ready yet."

---

## 3. Scope and Boundaries

### In-Scope

- The Sign In, Sample List, Create Sample, Sample Details, and View Report screens described above.
- The workflow status progression from Accessioned through Report Generated, and its visibility to the user.
- The validation and not-found error messages described above, needed so later negative test scenarios have real behavior to exercise.
- Running reliably, with predictable test data and behavior, both on a developer machine and inside the GitHub Actions pipeline (this application must be startable and stable in both places, though *how* it's packaged/started is an implementation detail for `/awos:tech`).

### Out-of-Scope

- The REST API test automation, UI test automation, Page Object Model, parallel execution, test-data builders, CI/CD pipeline, logging, and reporting — these belong to Phase 1, 2, and 3 of the roadmap and will each get their own specification.
- Real authentication/security hardening (this is a single, simple sign-in with no password recovery, multi-factor auth, or session-timeout behavior).
- Multiple user roles or permission levels — every signed-in user has the same access.
- Real bioinformatics, genetic analysis, or medically meaningful results.
- Production-grade or persistent storage — data does not need to survive an application restart.
- A polished or branded visual design — this only needs to be usable and stable enough for automated tests.

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- [YYYY-MM-DD] — [source reference] — [what behavior changed and why]
