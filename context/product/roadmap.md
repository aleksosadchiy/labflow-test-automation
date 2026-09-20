# Product Roadmap: Lab Workflow SDET Automation Framework

_This roadmap outlines the build sequence for a small, production-minded Java test automation framework that simulates a regulated laboratory workflow, prioritized to match what a senior SDET review evaluates most. It focuses on the "what" and "why," not implementation detail._

---

### Phase 0

_Before automation can begin: stand up the system under test. This is a practice project, so there is no existing application to point the framework at yet — that has to be built or chosen first._

- [ ] **Simulated Lab Backend (API Target)**
  - [x] **Minimal Mock/Stub API Service:** _(Done in `context/spec/002-mock-lab-rest-api/`: JSON API under `/api` for patients, orders, samples, stage changes, progress, results and reports.)_ Stand up a small local backend (e.g. WireMock stubs, JSON Server, or a lightweight Spring Boot app) exposing REST endpoints for the simulated workflow (patients, orders, samples, processing jobs, results, reports) that enforces the status state machine (Accessioned → ... → Report Generated) and produces the validation/error responses the negative test scenarios need to exercise.
  - [ ] **Runnable Locally & in CI:** _(Local part done in spec 002: one-command start via the Maven Wrapper and an open `/health` readiness check. Still open: the GitHub Actions job that starts it, which belongs to the Phase 3 CI/CD item.)_ Make sure the backend starts with a single command (or via Docker) on a developer machine and inside the GitHub Actions pipeline, so API tests always have a stable, repeatable target.

- [ ] **UI Target Application**
  - [x] **Tiny Front-End Over the Mock Backend:** Build a minimal front-end (Login, Sample List, Create Sample, Sample Details, Report screens) that talks to the mock lab backend, so UI automation exercises the same simulated domain and workflow as the API tests. _(See `context/spec/001-mock-lab-application/`. Underlying domain/service layer also built; the JSON REST API surface and CI-runnability remain open per the other Phase 0 sub-items.)_
  - [ ] **Stable Test Environment:** Confirm the front-end is reachable and stable enough for repeated automated runs (no CAPTCHAs, no rate-limiting, predictable test data/state), and runnable locally and in CI alongside the backend.

---

### Phase 1

_The foundation: project architecture and REST API test automation — the highest-priority skills for this role._

- [ ] **Maven Multi-Module Project Setup**
  - [ ] **Project Skeleton:** Stand up a parent POM with clearly separated modules (e.g. `common`, `api-client`, `api-tests`, `ui-tests`, `test-data`, or a simpler 3-module equivalent) so responsibilities are isolated from the start.
  - [ ] **Simulated Lab Domain Models:** Define the core entities of the simplified workflow — Patient, Order, Sample, Processing Job, Test Result, Report — and the status progression from Accessioned through Report Generated.

- [ ] **Core API Test Automation**
  - [ ] **API Client Layer:** Build a clean Test → Service/API layer → HTTP client separation (Retrofit + OkHttp, or RestAssured) so no test contains raw HTTP calls.
  - [ ] **Positive Workflow Coverage:** Automate the happy-path journey — create/retrieve a patient, create an order, register/retrieve a sample, verify initial status, start and complete processing, generate and retrieve a report.
  - [ ] **Negative Workflow Coverage:** Automate failure scenarios — missing mandatory fields, invalid patient ID, duplicate sample, invalid sample type, non-existing sample, unauthorized request, invalid status transition, malformed request body, unsupported HTTP method — asserting status code, response body, business fields, headers, and error messages (not status code alone).

---

### Phase 2

_Senior-level engineering capabilities: UI automation, reliable parallel execution, and isolated test data._

- [ ] **UI Automation with Page Object Model**
  - [ ] **Page Objects for Core Screens:** Implement Page Objects for the key UI flows (e.g. Login, Sample List, Create Sample, Sample Details, Report), encapsulating elements and actions rather than exposing locators to tests.
  - [ ] **Business-Readable UI Scenarios:** Write UI test scenarios that read as user workflows rather than locator collections.

- [ ] **Reliable, Isolated Test Execution**
  - [ ] **JUnit 5 Test Design:** Use parameterized tests and tags (`api`, `ui`, `smoke`, `regression`) to organize and target test runs.
  - [ ] **Thread-Safe Parallel Execution:** Configure JUnit 5 for concurrent execution across classes and methods without shared mutable state.
  - [ ] **Test-Data Builders & Isolation:** Provide reusable builders/factories that generate unique, isolated test data per test to prevent collisions under parallel runs.

- [ ] **Configuration & Logging**
  - [ ] **Externalized Configuration:** Remove hardcoded environment values in favor of CLI parameters or environment variables (base URL, API token, browser, environment).
  - [ ] **Structured Logging:** Adopt a real logging framework capturing request/response and business-identifier detail useful for debugging, while explicitly excluding credentials, tokens, and sensitive patient data.

---

### Phase 3

_Delivery and review readiness: CI/CD, reporting, documentation, and optional BDD polish._

- [ ] **CI/CD Pipeline**
  - [ ] **GitHub Actions Workflow:** Automate checkout, build, and test execution, with API and UI tests running as separate, independently schedulable jobs.
  - [ ] **Test Reporting in CI:** Publish pass/fail/skip/duration summaries (via Surefire, Allure, or JUnit XML) with enough failure detail to investigate issues from CI alone.

- [ ] **Documentation & Review Readiness**
  - [ ] **Project README:** Document the framework's structure, module responsibilities, and how to run tests locally and in CI.
  - [ ] **Clean Git History:** Maintain a readable commit history reflecting incremental, well-scoped changes.
  - [ ] **Architecture Walkthrough Readiness:** Be able to explain every item in the Definition of Done — structure, API layer, UI automation, Page Objects, test-data isolation, parallel execution, CI execution, failure investigation, and production-readiness gaps — and deliver the 2-minute project pitch.

- [ ] **Optional BDD Layer**
  - [ ] **Minimal Cucumber Demonstration:** Add one or two Gherkin scenarios (Given/When/Then) mapped to step definitions, purely to demonstrate BDD understanding — kept minimal and not prioritized over the items above.
