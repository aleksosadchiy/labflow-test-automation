# System Architecture Overview: Lab Workflow SDET Automation Framework

---

## 1. Automation Framework & Language Stack

- **Language & Runtime:** Java 17 (LTS) — required by the assignment; LTS gives a stable, well-supported baseline for `record`-based domain models and modern language features.
- **Build Tool:** Maven, multi-module (`common`, `api-client`, `api-tests`, `ui-tests`, `test-data`) — matches the assignment's recommended structure and keeps concerns cleanly separated.
- **Test Execution Framework:** JUnit 5 (Jupiter) — parameterized tests, tags (`api`, `ui`, `smoke`, `regression`), and the built-in parallel execution engine.
- **API Testing Library:** RestAssured. _Alternative: Retrofit + OkHttp._ RestAssured is chosen as the default because it is purpose-built for test automation (fluent given/when/then assertions, minimal boilerplate for a small endpoint surface), whereas Retrofit is optimized for building typed production API clients — better justified if the design discussion leans toward "client-library" design.
- **UI Automation Library:** Playwright for Java. _Alternative: Selenium WebDriver._ Playwright is the default given this repository's own name (`labflow-test-automation`), and it also ships auto-waiting and bundled browser binaries, reducing flakiness compared to hand-rolled Selenium waits — a plus for the "avoid flaky UI tests" evaluation criterion. Page Object Model applies equally well to either.
- **Optional BDD Layer (Phase 3):** Cucumber-JVM with Gherkin, for one or two demonstration scenarios only — kept minimal per the assignment's guidance not to over-invest here.

_Assumption: Java 17 and RestAssured are best-practice defaults, not explicitly mandated by the assignment (which allows either RestAssured or Retrofit+OkHttp) — confirm before locking in._

---

## 2. System Under Test (Mock Lab Application)

_Phase 0 of the roadmap: since no real laboratory system exists to test against, this project builds its own minimal target application, kept structurally separate from the test framework itself._

- **Mock Backend Framework:** Spring Boot (Java) — a small REST service exposing the simulated lab workflow endpoints (`/patients`, `/orders`, `/samples`, `/processing-jobs`, `/reports`) and enforcing the status state machine (Accessioned → ... → Report Generated), including the validation and error responses the negative test scenarios need. _Alternative: WireMock stub mappings or a Node.js `json-server`_ — simpler to stand up but harder to encode real state-transition logic and validation rules, which this project needs for meaningful negative testing.
- **In-Memory Data Store:** Plain in-memory Java collections (or H2 in-memory DB if persistence-like querying is useful). No external database — matches the assignment's explicit "no complicated databases" guidance.
- **Front-End Framework:** A minimal server-rendered UI via Spring Boot + Thymeleaf, serving the Login, Sample List, Create Sample, Sample Details, and Report screens directly from the same mock backend. _Alternative: a small standalone React/plain-JS single-page app calling the backend's REST API._ Thymeleaf is the default to avoid standing up a second toolchain (Node/npm) purely to host a throwaway UI — consistent with "do not over-engineer."
- **Location in Repo:** Lives in its own `mock-lab-app/` module, separate from the Maven multi-module automation framework, since it is the system under test, not the thing being delivered as the "product."

_Assumption: Spring Boot + Thymeleaf for the mock app is a best-practice default balancing realism with the assignment's "keep it small" guidance — confirm before building._

---

## 3. Test Data & Configuration

- **Test Data Generation:** Custom builders/factories (e.g. `PatientBuilder`, `SampleBuilder`) in the `test-data` module, using `java.util.UUID` to generate unique identifiers per test run and prevent collisions under parallel execution.
- **Test Data Storage:** None persisted between runs — all test data is ephemeral, created against the mock backend at test time and not required to survive beyond the run (traceability comes from correlation IDs in logs, not a database).
- **Configuration Management:** Environment variables and Maven system properties (`BASE_URL`, `API_TOKEN`, `BROWSER`, `ENVIRONMENT`), loaded through a small typed `Config` class. _Alternative: the Owner library_ for annotation-driven config binding — unnecessary at this project's scale.

---

## 4. Infrastructure & Deployment

- **CI/CD:** GitHub Actions, with separate `api-tests` and `ui-tests` jobs (plus a `build` job) so each can run and report independently.
- **Running the System Under Test:** The `mock-lab-app` is packaged as an executable Spring Boot jar and started as a background step before the test jobs run, both locally (`java -jar mock-lab-app.jar &`) and in CI. _Alternative: Docker Compose_, wrapping the mock app in a container for one-command startup — worth adopting only if local setup friction becomes a real problem, per "do not over-engineer."
- **Browser Automation Infra:** Playwright's bundled, auto-managed browser binaries (`mvn playwright:install`) — no separate Selenium Grid or browser-provisioning step needed.

---

## 5. Observability & Reporting

- **Logging:** SLF4J + Logback across both the test framework and the mock app, capturing request method, endpoint, correlation/test ID, response status, and business identifiers — explicitly never credentials, tokens, or patient-identifying data, per the assignment's regulated-environment guidance.
- **Test Reporting:** Maven Surefire reports and JUnit XML as the baseline (always available, no extra setup). _Alternative: Allure_, layered on top later if richer HTML reports become worth the added dependency and CI step.
- **CI Test Summaries:** GitHub Actions job summaries generated from the Surefire/JUnit XML output, showing total/passed/failed/skipped/duration directly in the workflow run.
