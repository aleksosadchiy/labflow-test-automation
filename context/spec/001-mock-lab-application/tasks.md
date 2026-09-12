# Tasks: Mock Lab Application (System Under Test)

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

Each slice leaves `mock-lab-app` in a runnable, `mvn spring-boot:run`-able state with one more piece of the functional spec working end-to-end.

---

- [x] **Slice 1: Sign In & Access Control**

  > A signed-in user reaches an (empty) Sample List; an unsigned-in user is redirected to Sign In.

  - [x] Task: Scaffold the `mock-lab-app` Spring Boot Maven module — standalone `pom.xml` (Spring Boot parent, `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-maven-plugin`), base package `com.mocklab`, and the `MockLabApplication` main class. Not a child of any future reactor. **[Agent: spring-boot-backend]**
  - [x] Task: Implement the `User` domain record, an in-memory `UserRepository`, and a `DataSeeder` (`CommandLineRunner`) that seeds one fixed login user on boot. **[Agent: spring-boot-backend]**
  - [x] Task: Implement `AuthService` (credential check) and `AuthController` — `GET /login`, `POST /login`, `POST /logout`, `GET /` — plus `LoginForm`, wired to the messages "Invalid username or password." and "Username and password are required." **[Agent: spring-boot-backend]**
  - [x] Task: Implement `SessionAuthInterceptor` + `WebConfig`, guarding every route except `/login`, `/`, and static assets, redirecting unauthenticated requests to `/login`. **[Agent: spring-boot-backend]**
  - [x] Task: Implement a minimal `GET /samples` route (`SampleWebController`) and `sample-list.html` rendering just the "No samples registered yet." empty state, plus `login.html` and a shared `fragments/layout.html`. **[Agent: spring-boot-backend]**
  - [x] Verify: Start the app, then via `curl` (with a cookie jar for session state): confirm `GET /login` returns 200; confirm `GET /samples` without a session redirects to `/login`; `POST /login` with blank fields shows "Username and password are required."; `POST /login` with wrong credentials shows "Invalid username or password."; `POST /login` with the seeded credentials followed by `GET /samples` returns 200 with "No samples registered yet." Stop the app and delete the cookie jar / any temp output. **[Agent: spring-boot-backend]**

- [ ] **Slice 2: Register a New Sample**

  > A signed-in user creates a sample and sees it appear in the list with status "Accessioned," with every Create-Sample validation message working.

  - [x] Task: Implement the `Patient` domain + `PatientRepository`, and extend `DataSeeder` to seed a small fixed set (~5–10) of patients with stable IDs. **[Agent: spring-boot-backend]**
  - [x] Task: Implement the `SampleType` enum and the `Order`, `Sample`, `ProcessingJob` domains + in-memory repositories (per the Data Model table). **[Agent: spring-boot-backend]**
  - [x] Task: Implement `SampleService.createSample(patientId, sampleType)` with the exact validation precedence from Technical Considerations §6 (blank patient → blank type → patient not found → invalid type → duplicate ID), creating `Order` + `Sample(ACCESSIONED)` + `ProcessingJob(ACCESSIONED)` together on success. **[Agent: spring-boot-backend]**
  - [x] Task: Implement `SampleWebController` routes `GET /samples/new` and `POST /samples`, `CreateSampleForm`, and `create-sample.html` — Post/Redirect/Get on success, re-render with the single relevant error message on failure. **[Agent: spring-boot-backend]**
  - [x] Task: Update `GET /samples` / `sample-list.html` to render real registered samples (identifier, patient, status) instead of only the empty state. **[Agent: spring-boot-backend]**
  - [ ] Verify: Sign in via `curl`, then: submit Create Sample with a seeded patient + valid type (confirm redirect and the new sample shows "Accessioned" in the list); submit with blank patient, blank type, an unknown patient ID, and an invalid sample type, confirming each exact message. Delete the cookie jar / any temp output. **[Agent: spring-boot-backend]**

- [ ] **Slice 3: View Sample Details & Advance the Workflow**

  > A signed-in user opens a sample, sees its full stage progression, and can manually advance it stage by stage until the Advance button disappears at the final stage.

  - [ ] Task: Implement the `SampleStatus` enum (8 stages, declaration order = workflow order) and `ProcessingJobService.advance(sampleId)` (moves to `values()[ordinal+1]`, syncs `Sample.status`, appends to `history`, no-ops at the final stage). **[Agent: spring-boot-backend]**
  - [ ] Task: Implement `GET /samples/{id}` and `sample-details.html` showing the current status and all 8 stages with completed/current/remaining styling, and the "Sample not found." message for an unknown ID. **[Agent: spring-boot-backend]**
  - [ ] Task: Implement `POST /samples/{id}/advance`, wired to `ProcessingJobService.advance()`; the Advance button is shown on the details page unless the sample is at `REPORT_GENERATED`. **[Agent: spring-boot-backend]**
  - [ ] Verify: Sign in via `curl`, create a sample, open its details (confirm current stage + full 8-stage list), then `POST` the advance route repeatedly and confirm the status moves one stage at a time and the Advance action stops changing anything once `REPORT_GENERATED` is reached; open details for a made-up ID and confirm "Sample not found." Delete the cookie jar / any temp output. **[Agent: spring-boot-backend]**

- [ ] **Slice 4: View Report**

  > Once a sample reaches "Report Generated," its report is viewable with sample ID, test name, result value, and generation date; earlier, viewing the report shows "Report is not ready yet."

  - [ ] Task: Implement the `TestResult` and `Report` domains + repositories, and `ReportService`, wired into `ProcessingJobService.advance()` so entering `RESULT_READY` creates the `TestResult` (fixed/deterministic placeholder values) and entering `REPORT_GENERATED` creates the `Report`. **[Agent: spring-boot-backend]**
  - [ ] Task: Implement `GET /samples/{id}/report` and `report.html` showing sample identifier, test name, result value, and generation date; add the "View Report" link on the details page once at `REPORT_GENERATED`; show "Report is not ready yet." otherwise. **[Agent: spring-boot-backend]**
  - [ ] Verify: Sign in via `curl`, create a sample, advance it through all 8 stages, open its report and confirm all four fields are present; create a second sample and confirm its report shows "Report is not ready yet." before advancing. Delete the cookie jar / any temp output. **[Agent: spring-boot-backend]**

- [ ] **Slice 5: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.

  - [ ] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic — `ProcessingJobService`, `SampleService`, `AuthService` — and controller/integration tests for each route's success and failure paths) based on the project's testing stack (JUnit 5 + AssertJ + `@WebMvcTest`/`MockMvc`, per Technical Considerations §8). Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 001-mock-lab-application` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [ ] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**
