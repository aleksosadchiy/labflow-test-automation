# Functional Specification: Mock Lab Programmatic Access (Lab Backend for Automated Clients)

- **Roadmap Item:** Phase 0 — Simulated Lab Backend (API Target): a machine-facing way to work with the mock lab, plus running it locally and in CI
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The Mock Lab Application already lets a person sign in and work through the lab workflow on screen. The automated test suites planned for the next phases, however, are not people at a keyboard: they are programs that need to register patients, place orders, register samples, move samples through processing, and read reports — quickly, repeatably, and without going through screens.

Today a program has no way to do any of that. It also cannot create its own patients or orders (only a small pre-loaded set of patients exists), and it cannot see the lab's rules being enforced with clear, readable rejection messages.

This specification adds a programmatic way to work with the same mock lab: everything an automated client does follows exactly the same lab rules as the screens, and anything it does is immediately visible on screen (and vice versa). It also covers starting the lab reliably, with one command, on a developer machine and in the automated build.

Success is measured by whether the API test suite in Phase 1 can automate every positive and negative scenario from the assignment against this lab, every time it is started, without any manual set-up.

---

## 2. Functional Requirements (The "What")

In this specification, the **client** is an automated program (such as a test suite) working with the lab. Every rejection tells the client, in a readable message, what went wrong.

### 2.1. Access Control

- **As a** client, **I want to** identify myself with a shared access key, **so that** only authorized programs can work with the lab.
  - **Acceptance Criteria:**
    - [ ] When a client makes any request that reads or changes lab records with the correct access key, then the request is accepted and processed.
    - [ ] When a client makes a request without an access key, then it is rejected as unauthorized with the message "Unauthorized."
    - [ ] When a client makes a request with an incorrect access key, then it is rejected as unauthorized with the message "Unauthorized."
    - [ ] When a client makes a request without an access key and the requested record does not exist, then it is still rejected as unauthorized (and not told whether the record exists).

### 2.2. Patients

- **As a** client, **I want to** register a new patient and look patients up, **so that** I can set up my own patients instead of depending on a fixed pre-loaded list.
  - **Acceptance Criteria:**
    - [ ] When a client registers a patient with a name, then the patient is created with a lab-assigned patient identifier and the client is shown the patient's identifier and name.
    - [ ] When a client registers a patient with the name missing or blank, then it is rejected with the message "Patient name is required."
    - [ ] When a client asks for a patient by an identifier that exists (including the pre-loaded patients), then it is shown that patient's identifier and name.
    - [ ] When a client asks for a patient by an identifier that does not exist, then it is told "Patient not found."

### 2.3. Orders

- **As a** client, **I want to** place an order for an existing patient and look orders up, **so that** the order-then-sample workflow of a real lab can be followed.
  - **Acceptance Criteria:**
    - [ ] When a client places an order for an existing patient, then the order is created with a lab-assigned order identifier and the client is shown the order identifier, the patient, and when it was created.
    - [ ] When a client places an order without naming a patient, then it is rejected with the message "Patient is required."
    - [ ] When a client places an order for a patient that does not exist, then it is rejected with the message "Patient not found."
    - [ ] When a client asks for an order by an identifier that exists, then it is shown that order's details.
    - [ ] When a client asks for an order by an identifier that does not exist, then it is told "Order not found."

### 2.4. Samples

- **As a** client, **I want to** register a sample for a patient (optionally under an order I already placed) and look samples up, **so that** it enters the lab workflow and can be tracked.
  - The lab always assigns the sample identifier; a client cannot choose one. A "duplicate sample" is therefore defined as a second sample of the same type under the same order.
  - **Acceptance Criteria:**
    - [ ] When a client registers a sample by giving a patient and a supported sample type, then the sample is created with a lab-assigned sample identifier and the status "Accessioned," and the client is shown the sample's identifier, patient, sample type, and status.
    - [ ] When a client registers a sample with the patient missing or blank, then it is rejected with the message "Patient is required."
    - [ ] When a client registers a sample with the sample type missing or blank, then it is rejected with the message "Sample type is required."
    - [ ] When a client registers a sample for a patient that does not exist, then it is rejected with the message "Patient not found."
    - [ ] When a client registers a sample with a sample type that is not one of the supported types, then it is rejected with the message "Invalid sample type."
    - [ ] When a client registers a sample with several problems at once, then only the first problem is reported, in this order: patient missing, sample type missing, patient not found, invalid sample type, order not found, order belongs to a different patient, duplicate sample type for the order. (The first four match the Create Sample screen.)
    - [ ] When a client asks for a sample by an identifier that exists, then it is shown the sample's identifier, patient, sample type, and current status.
    - [ ] When a client asks for a sample by an identifier that does not exist, then it is told "Sample not found."
    - [ ] When a client asks for the list of samples, then it is shown every registered sample, or an empty list if none are registered.
    - [ ] When a client registers a sample and names an existing order that belongs to the named patient, then the sample is attached to that order and the client is shown the order alongside the sample details.
    - [ ] When a client registers a sample without naming an order, then the lab creates a new order for the patient automatically, as the Create Sample screen does.
    - [ ] When a client registers a sample and names an order that does not exist, then it is rejected with the message "Order not found."
    - [ ] When a client registers a sample and names an order that belongs to a different patient than the one named, then it is rejected with the message "Order does not belong to this patient."
    - [ ] When a client registers a sample of a type that the named order already has a sample of, then it is rejected with the message "A sample of this type already exists for this order." and no new sample is created.
    - [ ] When a client registers a sample of a different type for an order that already has a sample, then it is accepted, and both samples appear under that order.

### 2.5. Moving a Sample Through the Workflow

- **As a** client, **I want to** ask for a sample to move to a specific stage, **so that** I can drive processing forward and confirm the lab refuses invalid jumps.
  - The stages, in order, are: Accessioned → Ready for Processing → Processing → Sequencing → Bioinformatics → Quality Check → Result Ready → Report Generated.
  - Only the stage immediately after the sample's current stage is accepted.
  - **Acceptance Criteria:**
    - [ ] When a client asks for a sample at "Accessioned" to move to "Ready for Processing," then the request is accepted and the client is shown the sample with the new status.
    - [ ] When a client asks for a sample to move through each stage in order, one request per stage, then every request is accepted and the sample ends at "Report Generated."
    - [ ] When a client asks for a sample at "Processing" to move straight to "Result Ready," then it is rejected with the message "Invalid status transition: cannot move from Processing to Result Ready." and the sample stays at "Processing."
    - [ ] When a client asks for a sample at "Result Ready" to move back to "Accessioned," then it is rejected with the message "Invalid status transition: cannot move from Result Ready to Accessioned." and the sample stays at "Result Ready."
    - [ ] When a client asks for a sample to move to the stage it is already at, then it is rejected with an invalid status transition message and the sample's status is unchanged.
    - [ ] When a client asks for a sample at "Report Generated" to move to any stage, then it is rejected with an invalid status transition message.
    - [ ] When a client asks for a sample to move to a stage name that does not exist, then it is rejected with the message "Invalid status."
    - [ ] When a client asks for a stage change without naming a stage, then it is rejected with the message "Status is required."
    - [ ] When a client asks for a stage change on a sample that does not exist, then it is told "Sample not found."
    - [ ] When a client moves a sample forward, then the new stage is shown on the Sample List and Sample Details screens for that sample.

### 2.6. Processing Progress, Results, and Reports

- **As a** client, **I want to** see how far a sample's processing has got and retrieve its result and report once ready, **so that** I can verify the outcome of the whole workflow.
  - **Acceptance Criteria:**
    - [ ] When a client asks for a sample's processing progress, then it is shown the sample's current stage and the ordered list of stages the sample has passed through, each with the time it was reached.
    - [ ] When a client asks for the processing progress of a sample that does not exist, then it is told "Sample not found."
    - [ ] When a client asks for the test result of a sample that has reached "Result Ready," then it is shown the sample identifier, the test name, and the result value.
    - [ ] When a client asks for the test result of a sample that has not yet reached "Result Ready," then it is told "Test result is not ready yet."
    - [ ] When a client asks for the report of a sample that has reached "Report Generated," then it is shown the sample identifier, the test name, the result value, and the date the report was generated — the same content as the View Report screen.
    - [ ] When a client asks for the report of a sample that has not yet reached "Report Generated," then it is told "Report is not ready yet."
    - [ ] When a client asks for the test result or report of a sample that does not exist, then it is told "Sample not found."

### 2.7. Handling Bad Requests

- **As a** client, **I want to** receive a clear rejection when my request is unreadable or uses an unsupported action, **so that** I can distinguish my own mistakes from lab rules being enforced.
  - **Acceptance Criteria:**
    - [ ] When a client sends a request whose content cannot be understood (for example, broken or incomplete data), then it is rejected with the message "Malformed request body." and nothing is created or changed.
    - [ ] When a client uses an action that the requested item does not support (for example, trying to delete a sample), then it is rejected with the message "Method not allowed." and nothing is changed.
    - [ ] When a client asks for something that does not exist at all, then it is told "Resource not found."
    - [ ] When a client's request is rejected for any reason, then the rejection includes a readable message explaining what went wrong.

### 2.8. Same Lab, Same Rules as the Screens

- **As a** lab-workflow user, **I want** anything done by a client to appear on screen and anything done on screen to be available to clients, **so that** UI tests and API tests can rely on one shared lab.
  - **Acceptance Criteria:**
    - [ ] When a client registers a sample, then a signed-in user opening the Sample List screen sees that sample with the status "Accessioned."
    - [ ] When a user registers a sample on the Create Sample screen, then a client can retrieve that sample and see the same details.
    - [ ] When several clients register samples at the same time, then every sample receives its own distinct identifier and none are lost or overwritten.
    - [ ] When two clients each move a different sample forward at the same time, then each sample ends at the stage that client asked for and neither is affected by the other.

### 2.9. Starting the Lab Reliably

- **As a** developer or build pipeline, **I want to** start the lab with a single command and know when it is ready, **so that** tests always have a stable, repeatable target.
  - **Acceptance Criteria:**
    - [ ] When a developer runs the single documented start command on a machine with the required tools installed, then the lab starts with no further manual steps.
    - [ ] When the lab has finished starting, then a simple readiness check (which does not need an access key) reports that it is ready.
    - [ ] When the readiness check is asked before the lab has finished starting, then it does not report ready.
    - [ ] When the lab starts, then it begins with the same known set of pre-loaded patients and the same sign-in user every time, and no samples, orders, or other records from earlier runs.
    - [ ] When the lab is started with a chosen access key, then only that key is accepted; when none is chosen, then a documented default key for local use is accepted.
    - [ ] When the lab is started in the automated build, then it becomes ready within 30 seconds.

---

## 3. Scope and Boundaries

### In-Scope

- A programmatic way for automated clients to: register and look up patients; place and look up orders; register, list, and look up samples; move a sample to its next stage; see a sample's processing progress; and retrieve a sample's test result and report.
- Shared-key access control for these requests, with a clear rejection when the key is missing or wrong.
- Readable rejection messages for every failure case listed above (missing information, unknown records, invalid sample type, invalid stage change, unreadable request, unsupported action).
- The same lab rules and shared records as the existing screens, so work done either way is visible in both.
- A single documented command to start the lab, a readiness check, and a known clean starting state, usable on a developer machine and in the automated build.

### Out-of-Scope

- The API test suite itself, the framework that calls the lab, test-data builders, and parallel test execution — these belong to Phases 1 and 2 of the roadmap and get their own specifications.
- The GitHub Actions workflow that starts the lab and runs tests, test reporting, and logging improvements — covered by the CI/CD, reporting, and logging roadmap items.
- Packaging the lab in a container.
- Changing, deleting, or searching patients, orders, or samples (only registering, looking up, listing samples, and moving forward through stages). Test isolation relies on each test creating its own uniquely identified records, not on cleaning up afterward.
- Paging or filtering of the sample list; the dataset is always small.
- Real authentication and security hardening: one shared access key, no per-user keys, expiry, roles, or permissions.
- Any change to the existing screens' look or behavior.
- Real laboratory science, persistent storage across restarts, and polished visual design (unchanged from the first specification).

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- [YYYY-MM-DD] — [source reference] — [what behavior changed and why]
