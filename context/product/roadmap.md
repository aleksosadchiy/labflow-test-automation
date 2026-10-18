# Product Roadmap: LabFlow Test Automation

_This roadmap outlines the build sequence for a small, production-minded Java test automation framework that simulates a regulated laboratory workflow, prioritized per the project brief's own ranking (section 30). It focuses on the "what" and "why," not implementation detail._

---

### Phase 0

_Before automation can begin: stand up the system under test. This is a practice project, so there is no existing application to point the framework at yet — that has to be built or chosen first._

- [x] **Simulated Lab Backend (API Target)**
  - [x] **Minimal Mock/Stub API Service:** _(Done in `context/spec/002-mock-lab-rest-api/`: JSON API under `/api` for patients, orders, samples, stage changes, progress, results and reports.)_ Stand up a small local backend (e.g. WireMock stubs, JSON Server, or a lightweight Spring Boot app) exposing REST endpoints for the simulated workflow (patients, orders, samples, processing jobs, results, reports) that enforces the status state machine (Accessioned → ... → Report Generated) and produces the validation/error responses the negative test scenarios need to exercise.
  - [x] **Runnable Locally & in CI:** _(Local part done in spec 002: one-command start via the Maven Wrapper and an open `/health` readiness check. CI part done in `context/spec/006-ci-cd-pipeline/`: each check job starts the lab with `.github/scripts/lab.sh` and waits for `/health`.)_ Make sure the backend starts with a single command (or via Docker) on a developer machine and inside the GitHub Actions pipeline, so API tests always have a stable, repeatable target.

- [x] **UI Target Application**
  - [x] **Tiny Front-End Over the Mock Backend:** Build a minimal front-end (Login, Sample List, Create Sample, Sample Details, Report screens) that talks to the mock lab backend, so UI automation exercises the same simulated domain and workflow as the API tests. _(See `context/spec/001-mock-lab-application/`. Underlying domain/service layer also built; the JSON REST API surface was done in spec 002 and CI-runnability in spec 006.)_
  - [x] **Stable Test Environment:** _(Done in `context/spec/006-ci-cd-pipeline/`: the UI tests job (named Screen checks until 2026-10-18) runs the screens against its own lab on every push and PR.)_ Confirm the front-end is reachable and stable enough for repeated automated runs (no CAPTCHAs, no rate-limiting, predictable test data/state), and runnable locally and in CI alongside the backend.

---

### Phase 1

_The foundation: project architecture and REST API test automation — the highest-priority skills for this role._

- [x] **Maven Multi-Module Project Setup** _(Done in `context/spec/003-test-automation-foundation/`.)_
  - [x] **Project Skeleton:** Stand up a parent POM with clearly separated modules (e.g. `common`, `api-client`, `api-tests`, `ui-tests`, `test-data`, or a simpler 3-module equivalent) so responsibilities are isolated from the start.
  - [x] **Simulated Lab Domain Models:** Define the core entities of the simplified workflow — Patient, Order, Sample, Processing Job, Test Result, Report — and the status progression from Accessioned through Report Generated.

- [x] **Core API Test Automation** _(Done in `context/spec/003-test-automation-foundation/`.)_
  - [x] **API Client Layer:** Build a clean Test → Service/API layer → HTTP client separation (Retrofit + OkHttp, or RestAssured) so no test contains raw HTTP calls.
  - [x] **Positive Workflow Coverage:** Automate the happy-path journey — create/retrieve a patient, create an order, register/retrieve a sample, verify initial status, start and complete processing, generate and retrieve a report.
  - [x] **Negative Workflow Coverage:** Automate failure scenarios — missing mandatory fields, invalid patient ID, duplicate sample, invalid sample type, non-existing sample, unauthorized request, invalid status transition, malformed request body, unsupported HTTP method — asserting status code, response body, business fields, headers, and error messages (not status code alone).

---

### Phase 2

_Advanced engineering capabilities: UI automation, reliable parallel execution, and isolated test data._

- [x] **UI Automation with Page Object Model** _(Done in `context/spec/004-ui-automation-page-objects/`.)_
  - [x] **Page Objects for Core Screens:** Implement Page Objects for the key UI flows (e.g. Login, Sample List, Create Sample, Sample Details, Report), encapsulating elements and actions rather than exposing locators to tests.
  - [x] **Business-Readable UI Scenarios:** Write UI test scenarios that read as user workflows rather than locator collections.

- [x] **Reliable, Isolated Test Execution** _(Done in `context/spec/005-reliable-isolated-execution/`.)_
  - [x] **JUnit 5 Test Design:** Use parameterized tests and tags (`api`, `ui`, `smoke`, `regression`) to organize and target test runs.
  - [x] **Thread-Safe Parallel Execution:** Configure JUnit 5 for concurrent execution across classes and methods without shared mutable state.
  - [x] **Test-Data Builders & Isolation:** Provide reusable builders/factories that generate unique, isolated test data per test to prevent collisions under parallel runs.

- [x] **Configuration & Logging** _(Done in `context/spec/005-reliable-isolated-execution/`.)_
  - [x] **Externalized Configuration:** Remove hardcoded environment values in favor of CLI parameters or environment variables (base URL, API token, browser, environment).
  - [x] **Structured Logging:** Adopt a real logging framework capturing request/response and business-identifier detail useful for debugging, while explicitly excluding credentials, tokens, and sensitive patient data.

---

### Phase 3

_Delivery: CI/CD, test reporting and a visual report of every run._

- [x] **CI/CD Pipeline** _(Done in `context/spec/006-ci-cd-pipeline/`.)_
  - [x] **GitHub Actions Workflow:** Automate checkout, build, and test execution, with API and UI tests running as separate, independently schedulable jobs.
  - [x] **Test Reporting in CI:** Publish pass/fail/skip/duration summaries (via Surefire, Allure, or JUnit XML) with enough failure detail to investigate issues from CI alone.
  - [x] **Visual Report of a Run:** _(Done in `context/spec/007-allure-report/`.)_ Keep one visual report per run, covering both check families, that shows each check's requests and a failed screen check's picture and recording; it is kept with every automatic run and linked from the run page, and it never changes the run's verdict.
