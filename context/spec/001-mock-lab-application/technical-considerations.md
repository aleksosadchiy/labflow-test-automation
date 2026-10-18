# Technical Considerations: Mock Lab Application (System Under Test)

- Roadmap Item: Phase 0 — Simulated Lab Backend (API Target) & UI Target Application
- Related Functional Spec: `functional-spec.md`
- Status: Completed

## 1. High-Level Technical Approach

Build one self-contained Spring Boot module, `mock-lab-app/`, that serves both a server-rendered Thymeleaf UI (the 5 in-scope screens) and the REST endpoints named in the architecture doc, backed by a single in-memory domain/service layer shared by both. No external database, no separate front-end toolchain: the whole app packages as one executable jar (`mock-lab-app.jar`) that starts in a few seconds, seeds a fixed set of reference data (patients, one login user) on every boot, and resets to that same clean state on every restart. The state machine is a small fixed-order enum walked one step at a time by an explicit "advance" action — no scheduling, no async processing, nothing that isn't directly triggered by a user (or test) action. Everything is sized to be *just enough* to make the 5 screens and later negative/positive API tests meaningful, not to resemble a real LIMS.

_Assumption: base Java package is `com.mocklab`; adjust freely once the module is actually scaffolded — named here only so the Component Breakdown can give concrete paths._

## 2. Architecture Changes

This is the first module in the repository — there is nothing to migrate or integrate with yet.

- **New module:** `mock-lab-app/` at the repo root, with its own `pom.xml` (Spring Boot parent, `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-maven-plugin` for the executable jar). It is *not* a child module of the future test-automation Maven reactor (`common`, `api-client`, `api-tests`, `ui-tests`, `test-data`) — architecture explicitly keeps the system-under-test structurally separate from the framework that tests it.
- **Two request surfaces, one domain layer:** the Thymeleaf MVC controllers (this doc's focus, Section 4) and the JSON REST controllers (`/patients`, `/orders`, `/samples`, `/processing-jobs`, `/reports` — named in architecture, detailed contract left to a later API-test-focused spec since this functional spec's in-scope is only the 5 UI screens) both call into the same `service` package, so the state machine and validation rules are enforced identically no matter which surface a test hits.
  _Assumption: full JSON request/response shapes for the REST surface are out of scope for this document and will get their own technical-considerations pass alongside the API test-automation work in Phase 1 — this doc only guarantees the package/service boundary that makes that later work additive rather than a rewrite._
- **No persistence layer added:** plain in-memory repositories (Section 5) — no JPA, no H2 unless a later need for query-like lookups emerges (architecture allows H2 as an alternative but plain collections are sufficient for this data size and access pattern).
- **New cross-cutting concern:** authentication/session handling, needed for the first time here (Section 6/7).

## 3. Data Model

All entities are plain in-memory records held in `ConcurrentHashMap<String, T>`-backed repositories, keyed by a string business ID. Nothing survives a JVM restart.

| Entity | Key Fields | Relationships | Notes |
|---|---|---|---|
| `User` | `username`, `password` | none | Login credential for the UI only. Seeded at startup with one fixed account (e.g. `labtech` / `password123`). |
| `Patient` | `id` (e.g. `PAT-1001`), `name` | 1—many `Order` | **No create-patient screen exists**, so patients must pre-exist. Seeded at startup from a small fixed in-code list (~5–10 patients) so both UI testers and API testers can reference known, stable IDs. _Assumption: seeding happens in a `CommandLineRunner`/`ApplicationRunner` bean, not from a config file, since the list is small and fixed — call this out if a later spec wants file-driven seed data instead._ |
| `Order` | `id`, `patientId`, `createdAt` | many—1 `Patient`; 1—1 `Sample` | Not surfaced on any of the 5 screens. Auto-created behind the scenes whenever a Sample is registered, purely so the domain model matches the project brief's Patient→Order→Sample chain and the `/orders` endpoint has real data to return. |
| `Sample` | `id`, `patientId`, `orderId`, `sampleType`, `status`, `createdAt` | many—1 `Patient`; 1—1 `Order`; 1—1 `ProcessingJob` | `status` here is a denormalized mirror of `ProcessingJob.currentStatus`, kept in sync on every advance, so the Sample List screen can render status without a join. `sampleType` is one of a fixed enum (e.g. `BLOOD`, `TISSUE`, `SWAB`, `URINE` — _Assumption: exact supported set to be confirmed, not specified in the functional spec_). |
| `ProcessingJob` | `id`, `sampleId` (unique), `currentStatus`, `history` (ordered list of `{status, timestamp}`) | 1—1 `Sample` | Owns the actual state-machine value; `history` isn't required by any screen but costs little and gives the Report screen a real "date generated" and gives future tests something to assert on for state-transition auditing. |
| `TestResult` | `id`, `sampleId`, `testName`, `resultValue`, `generatedAt` | 1—1 `Sample` | Auto-created the moment the job transitions into `RESULT_READY`. Value is a fixed/deterministic placeholder (no real bioinformatics, per non-goals) — e.g. a canned `testName` and a simple generated `resultValue`. |
| `Report` | `id`, `sampleId`, `generatedAt` | 1—1 `Sample`; logically 1—1 `TestResult` | Auto-created the moment the job transitions into `REPORT_GENERATED`. The Report screen reads `Sample` + `TestResult` + `Report` together. |

_Assumption — sample ID and the "duplicate sample" error: the Create Sample **form** takes only patient + sample type (per functional spec), so the sample ID is server-generated and a collision is effectively impossible through the UI. The "A sample with this ID already exists." message is real validation logic living in `SampleService` (checked defensively even for generated IDs), and becomes actually reachable once the REST API — which is expected to accept a caller-supplied sample ID, matching the project brief's "duplicate sample" negative API scenario — is built in Phase 1. Flagging this now so the UI/API inconsistency in the source functional spec isn't silently resolved one way without sign-off._

## 4. API Contracts (Thymeleaf/MVC routes backing the 5 screens)

Spring MVC controller routes, server-rendered HTML, Post/Redirect/Get on every state-changing form. No JSON here — the JSON REST surface (`/patients`, `/orders`, `/samples`, `/processing-jobs`, `/reports`) is a separate, later contract (see Section 2).

| Method & Path | Purpose | Success behavior | Failure/edge behavior |
|---|---|---|---|
| `GET /login` | Render Sign In screen | 200, `login.html` | — |
| `POST /login` | Submit credentials (`username`, `password`) | Creates session, 302 → `/samples` | Blank field(s) → re-render with "Username and password are required."; bad credentials → re-render with "Invalid username or password." |
| `POST /logout` | End session | Invalidate session, 302 → `/login` | — _Assumption: not mentioned in the functional spec but needed for a usable/testable login cycle; adding it as a small, obvious addition._ |
| `GET /samples` | Sample List screen | 200, `sample-list.html` with rows of `{sampleId, patientName, status}`; "No samples registered yet." when empty | Unauthenticated → 302 → `/login` |
| `GET /samples/new` | Create Sample form | 200, `create-sample.html` | Unauthenticated → 302 → `/login` |
| `POST /samples` | Submit Create Sample form (`patientId`, `sampleType`) | Creates `Order`+`Sample`(status `ACCESSIONED`)+`ProcessingJob`; 302 → `/samples/{id}` | Re-render `create-sample.html` (values preserved) with the single most relevant field error: "Patient is required." / "Sample type is required." / "Patient not found." / "Invalid sample type." / "A sample with this ID already exists." |
| `GET /samples/{id}` | Sample Details & Workflow Progress screen | 200, `sample-details.html`: all 8 stages with completed/current/remaining styling; Advance button shown unless status is `REPORT_GENERATED` | Unknown ID → 200 (or 404 — see assumption below), page/message "Sample not found." |
| `POST /samples/{id}/advance` | Advance one stage | Moves `ProcessingJob.currentStatus` to the next stage, syncs `Sample.status`, fires side-effects (auto `TestResult` / auto `Report` on the relevant transitions), 302 → `/samples/{id}` | Already at `REPORT_GENERATED` → button isn't rendered so this shouldn't be reachable from the UI; defensively no-ops rather than erroring if hit directly. Unknown ID → same "Sample not found." handling as above. |
| `GET /samples/{id}/report` | View Report screen | If status is `REPORT_GENERATED`: 200, `report.html` with `{sampleId, testName, resultValue, generatedAt}` | If not yet `REPORT_GENERATED`: 200, message "Report is not ready yet." (not an error — this is expected, spec'd UI behavior) |
| `GET /` | Root convenience redirect | 302 → `/samples` if session present, else → `/login` | _Assumption: convenience addition, not in functional spec._ |

_Assumption — HTTP method for Advance: a plain HTML form only submits GET/POST, so `POST /samples/{id}/advance` is used rather than PUT/PATCH, even though semantically "advance" is closer to a PATCH. Worth flagging because it is not idempotent (calling it twice moves two stages) — a deliberate, discussable contrast if the later JSON REST API models the same transition as an idempotent "set status to X" PUT/PATCH instead._

_Assumption — "Sample not found." status code: rendering it as a normal 200 HTML page (not a raw 404) matches how the other screens are written (friendly message, not a framework error page); if UI test automation later needs to assert on HTTP status for this case, that's worth confirming explicitly then._

_Design note (not just an assumption) — the "Patient" field on the Create Sample form is a plain text input for the patient ID, not a dropdown of seeded patients. A dropdown would make "Patient not found." unreachable through the UI itself; free text keeps every functional-spec error path exercisable by UI automation, not only by the future API tests._

## 5. Component Breakdown

```
mock-lab-app/
  src/main/java/com/mocklab/
    MockLabApplication.java            # main class

    web/                               # Thymeleaf-facing MVC controllers (Section 4)
      AuthController.java              # GET/POST /login, POST /logout, GET /
      SampleWebController.java         # /samples, /samples/new, /samples/{id}, /samples/{id}/advance
      ReportWebController.java         # /samples/{id}/report
      form/
        LoginForm.java                 # username, password
        CreateSampleForm.java          # patientId, sampleType

    api/                                # JSON REST controllers — placeholders only, full contract deferred (Section 2)
      PatientApiController.java
      OrderApiController.java
      SampleApiController.java
      ProcessingJobApiController.java
      ReportApiController.java

    domain/                            # entities (Section 3)
      Patient.java, Order.java, Sample.java, ProcessingJob.java,
      TestResult.java, Report.java, User.java
      SampleType.java                  # enum
      SampleStatus.java                # enum, declaration order == state-machine order (Section 6)

    repository/                        # in-memory stores
      PatientRepository.java, OrderRepository.java, SampleRepository.java,
      ProcessingJobRepository.java, TestResultRepository.java, ReportRepository.java,
      UserRepository.java

    service/                           # domain/business logic, shared by web/ and api/
      AuthService.java                 # credential check
      PatientService.java              # lookup (seeded, read-only)
      SampleService.java               # create + validate (Section 6)
      ProcessingJobService.java        # state machine + advance (Section 6)
      ReportService.java               # TestResult/Report auto-generation on transition

    config/
      DataSeeder.java                  # CommandLineRunner: seeds Users + Patients on boot
      WebConfig.java                   # registers the auth interceptor, static resource handling
      SessionAuthInterceptor.java      # HandlerInterceptor: no session -> redirect /login (excludes /login, /, static assets)

    exception/
      NotFoundException.java
      ValidationException.java         # carries the exact user-facing message string

  src/main/resources/
    templates/
      fragments/layout.html            # shared header/nav, login-state aware
      login.html
      sample-list.html
      create-sample.html
      sample-details.html
      report.html
    static/
      (minimal CSS only — no design polish per non-goals)
```

_Assumption — auth mechanism: a hand-rolled `SessionAuthInterceptor` over plain `HttpSession`, not Spring Security. Rationale: "real auth/security hardening" is an explicit non-goal, and a full security framework brings CSRF tokens, filter chains, and configuration overhead disproportionate to one login screen. Trade-off made explicit: **no CSRF protection** on the POST forms — acceptable for a throwaway test target, called out so it doesn't read as an oversight later._

_Assumption — the JSON REST API's own auth (referenced by the project brief's `API_TOKEN` env var and the "unauthorized request" negative scenario) is a separate, simple shared-secret bearer-token check via a `Filter` on the `api/` package's routes only — intentionally decoupled from the UI's session cookie, since they're different consumers (browser vs. HTTP client) with different lifecycles. Full detail deferred to the same later API-focused spec as Section 2/4._

## 6. Logic / Algorithm

**Status state machine** (`SampleStatus` enum, in declaration order):

```
ACCESSIONED → READY_FOR_PROCESSING → PROCESSING → SEQUENCING →
BIOINFORMATICS → QUALITY_CHECK → RESULT_READY → REPORT_GENERATED
```

- **Advance:** `ProcessingJobService.advance(sampleId)` finds the job, takes `currentStatus.ordinal()`, and — if not already the last enum constant — sets `currentStatus` to `values()[ordinal + 1]`, appends `{newStatus, now}` to `history`, and syncs `Sample.status`. Nothing else is timer- or event-driven; every transition is caused by exactly one explicit call.
- **Final-stage detection:** `status == SampleStatus.REPORT_GENERATED` (equivalently `ordinal() == values().length - 1`). This single check drives both "hide the Advance button" and "show the report" on the Sample Details screen.
- **Side effects wired to specific transitions**, executed inside the same `advance()` call so they're atomic with the status change:
  - Entering `RESULT_READY` → `ReportService` creates the `TestResult` row (fixed/deterministic placeholder `testName`/`resultValue` — no real bioinformatics).
  - Entering `REPORT_GENERATED` → `ReportService` creates the `Report` row with `generatedAt = now`.

**Create Sample validation** (`SampleService.createSample(patientId, sampleType)`), applied in this order, stopping at the first failure — _Assumption: functional spec doesn't specify precedence when multiple fields are wrong at once; single-message-per-submission, checked in the order below, is the simplest rule that satisfies every example given:_
1. `patientId` blank → `"Patient is required."`
2. `sampleType` blank → `"Sample type is required."`
3. `patientId` not found in `PatientRepository` → `"Patient not found."`
4. `sampleType` not one of the fixed `SampleType` enum values (case-insensitive match attempted first) → `"Invalid sample type."`
5. Generated sample ID already present in `SampleRepository` (defensive; see Section 3 assumption on how this becomes reachable) → `"A sample with this ID already exists."`
6. All pass → generate ID, persist `Order` + `Sample(status=ACCESSIONED)` + `ProcessingJob(ACCESSIONED)` together.

## 7. Impact and Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| In-memory data resets on every restart | Any test relying on data surviving an app restart mid-suite will fail; conversely this *is* what gives every run a clean, deterministic slate | Document the fixed seed set (patient IDs, login user) as a stable fixture reference for test authors; never expose a "reset" endpoint that could be called mid-suite by accident — only process restart resets state |
| Concurrent/parallel access (Phase 2 introduces parallel JUnit execution against this same running instance) | Two threads advancing the same sample, or racing on sample-ID generation, could corrupt state or create duplicate IDs | `ConcurrentHashMap`-backed repositories; `synchronized` (or per-sample lock) around the read-modify-write in `advance()` and around ID generation-and-insert in `createSample()` |
| Hand-rolled session auth instead of Spring Security | No CSRF protection; simpler but non-standard | Acceptable given the explicit "no production-grade auth" non-goal; documented plainly in Section 5 rather than silently omitted |
| Two distinct auth mechanisms in one app (UI session cookie vs. API bearer token) | Could confuse test authors about which applies where, or leak one into the other's routes | Strict path separation (UI under `/`, `/login`, `/samples/**`; JSON API under its own routes) and the interceptor/filter each only guarding its own prefix |
| No real bioinformatics — `TestResult`/`Report` values are fixed placeholders | Some report-content assertions in later tests will be trivially satisfied, not meaningfully validated | Acceptable, explicit non-goal; called out so nobody mistakes canned data for a modeling gap |
| Module isn't yet part of any Maven multi-module reactor (that reactor doesn't exist until Phase 1) | Risk of rework when the test-automation parent POM is introduced later | Keep `mock-lab-app/` fully self-contained (own `pom.xml`, own `spring-boot-maven-plugin` build), so it plugs into a future reactor as an unrelated sibling module rather than needing restructuring |
| Startup time / flakiness of "start as a background process" in CI | If the jar isn't ready when test jobs start, every downstream job fails for an infrastructure reason, not a test failure | Out of scope for this document (owned by the CI/CD spec), but worth noting the app should expose a cheap readiness signal (e.g., `GET /login` returning 200) that a CI wait-step can poll |

## 8. Testing Strategy

This covers the mock app's *own* correctness — not the later API/UI test-automation framework, which is out of scope here.

- **Unit tests (JUnit 5 + AssertJ), no Spring context:**
  - `ProcessingJobService`: advancing from each of the 8 stages lands on the correct next stage; advancing from `REPORT_GENERATED` is a no-op; the `RESULT_READY`/`REPORT_GENERATED` side effects (TestResult/Report creation) fire exactly once, on exactly the right transition.
  - `SampleService`: each negative case from Section 6 (blank patient, blank type, unknown patient, invalid type, duplicate ID) returns the exact specified message; the precedence order is verified with multiple-fields-wrong inputs.
  - `PatientService` / `DataSeeder`: seeded patients exist and are resolvable by ID after startup.
  - `AuthService`: valid/invalid/blank credential combinations return the right outcome/message.
- **Controller slice tests** (`@WebMvcTest` or full `@SpringBootTest` + `MockMvc`):
  - Each route in Section 4 returns the right view/redirect and model attributes for its success and failure paths.
  - Unauthenticated requests to any protected route redirect to `/login`.
  - Form POSTs follow Post/Redirect/Get on success and re-render with a preserved, correct error message on failure.
- **Context-load smoke test:** a plain `@SpringBootTest` that only asserts the application context starts — catches wiring/bean mistakes immediately, since every later phase of this project depends on this app always starting cleanly.
- **Manual pre-completion checklist:** before calling this module done, start the jar locally and click through all 5 screens end-to-end once (login → sample list → create sample → advance through all 8 stages → view report), plus each documented error path — cited as evidence in the completion report, not asserted from memory.
