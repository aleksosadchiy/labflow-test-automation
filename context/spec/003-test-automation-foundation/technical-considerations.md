# Technical Specification: Test Automation Foundation and Lab Workflow Checks (Phase 1)

- **Functional Specification:** `context/spec/003-test-automation-foundation/functional-spec.md`
- **Status:** Completed
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Create the test framework as a Maven multi-module reactor at the repository root, next to (but not including) the standalone `mock-lab-app`. Three modules only, matching the functional spec: `common` (the lab's shared concepts), `api-client` (the only code that talks to the lab), `api-tests` (the workflow checks). Tests follow a strict layering: **Test → Service → Api → RestAssured**. A check never contains HTTP details.

Key design ideas, each easy to explain:

1. **Two ways to call every operation.** A "happy" method returns a domain object and fails loudly on an unexpected status; an `attempt…` twin returns a `LabResponse<T>` wrapper (status, headers, typed body, error) for scenarios where a refusal is the *expected* result. Refusals are outcomes, not exceptions.
2. **Custom AssertJ assertions** (`assertThat(response).isRejectedAs(CONFLICT, "…")`) so rejection checks read as business statements and verify kind, exact message and headers in one place.
3. **Fail fast before any check runs:** a JUnit Platform `LauncherSessionListener` verifies once that the lab is reachable and accepts the key, and aborts the whole run with one clear message otherwise.
4. **The key can never leak:** it is accepted only through the `API_TOKEN` environment variable, is wrapped in a type whose `toString()` is `***`, and the `Authorization` header is blacklisted in RestAssured's logging.
5. **Independent, repeatable checks:** each check creates its own uniquely named records, nothing static and mutable, random test order enabled to *prove* independence.

No new services, no Docker, no database, no CI wiring (Phase 3). The lab is started separately by the engineer.

_Assumption: base Java package `com.labflow`, Maven groupId `com.labflow`, root artifactId `lab-test-automation`, version `1.0.0-SNAPSHOT` (deliberately different from the lab's `com.mocklab`)._

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1 Architecture Changes

| Change | Detail |
|---|---|
| New reactor | Root `pom.xml` (packaging `pom`) with modules `common`, `api-client`, `api-tests`. It does **not** list `mock-lab-app`, `ui-tests` or `test-data`. |
| Root Maven Wrapper | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` at the repo root, script-only mode, Maven 3.9.11 (same as the lab, so both share one cached distribution). The lab keeps its own wrapper; Maven finds the nearest `.mvn` walking up from the working directory, so there is no conflict. |
| Dependency direction | `api-tests → api-client → common`. `common` has no third-party runtime dependencies. |
| Lab stays outside | The top-level command can never build, start or stop the lab (functional spec §2.1 AC4). |
| No new lab changes | The lab is used as-is (spec 002 contract). |

**Root wrapper generation** (verified in a spike): with the root `pom.xml` in place, run from the repo root `mock-lab-app/mvnw -q wrapper:wrapper -Dmaven=3.9.11 -Dtype=only-script`. The repo's `*.jar` ignore rule is irrelevant because script-only mode has no jar.

### 2.2 Build Design (Maven)

| Item | Decision |
|---|---|
| Java level | `maven.compiler.release=17` (verified on the local JDK 26: class files are major 61 and Java 21 APIs are rejected). No toolchains, no Enforcer. |
| Root `dependencyManagement` | `junit-bom` 5.14.4, `rest-assured` 5.5.7, `jackson-bom` 2.19.2, `assertj-core` 3.27.7, and the three sibling modules at `${project.version}`. |
| Root `pluginManagement` | `maven-compiler-plugin` 3.15.0 and `maven-surefire-plugin` 3.6.0 versions only; Surefire stays at defaults. |
| Children | No versions and no properties of their own. |
| Test plugin | **Surefire** (`*Test` naming, `test` phase), not Failsafe. Design note: Failsafe earns its keep when a `post-integration-test` teardown must still run (stop a server/container) or slow tests need separating; here the tests neither start nor stop the lab, and Failsafe would turn "one command" into `verify`. |
| Surefire settings that must stay default | `failIfNoTests=false` (modules `common`/`api-client` legitimately have few tests), no `testFailureIgnore`, no `redirectTestOutputToFile` (it would hide the pre-flight message). |
| Versions | JUnit 5.x line (architecture says JUnit 5). JUnit 6.1 and RestAssured 6.0 exist but were not spiked. |

**Commands (verified in a spike):**

| Purpose | Command |
|---|---|
| All checks | `./mvnw clean test` (from the repo root) |
| Only checks that use the lab (+ the framework's quick self-checks) | `./mvnw -pl api-tests -am test` |

`-am` (`--also-make`) builds the modules `api-tests` depends on, because `test` does not install artifacts and plain `-pl api-tests` fails on a clean machine with "Could not find artifact …:api-client". Never document `-q` (it hides the "Tests run" summary and BUILD line), and never use `install` in the documented flow (stale sibling jars in `~/.m2`).

**README update (minimal, this spec):** replace the "Running Tests" section with the two-terminal flow — terminal 1 `cd mock-lab-app && ./mvnw spring-boot:run` (wait for `/health`), terminal 2 the two commands above — list required tools (a Java 17+ runtime, internet the first time), the `BASE_URL`/`-DbaseUrl` and `API_TOKEN` settings, and remove the not-yet-available `ui-tests` command. The full README stays Phase 3.

### 2.3 Data Model (shared domain, `common`)

Plain Java records/enums, no annotations, mapped from the lab's JSON (spec 002 §2.3). The Jackson mapper lives in `api-client`, keeping `common` dependency-free.

| Type | Fields / members |
|---|---|
| `Patient` | `id`, `name` |
| `Order` | `id`, `patientId`, `createdAt` (Instant), `samples` (list of `SampleSummary`) |
| `SampleSummary` | `id`, `sampleType`, `status` |
| `Sample` | `id`, `patientId`, `orderId`, `sampleType`, `status`, `createdAt` |
| `Progress` | `sampleId`, `currentStatus`, `stages` (list of `StageEntry(status, reachedAt)`) — the "processing job" concept |
| `TestResult` | `sampleId`, `testName`, `resultValue` |
| `Report` | `sampleId`, `testName`, `resultValue`, `generatedAt` |
| `ApiError` | `status`, `error`, `message`, `path`, `timestamp` |
| `SampleType` | `BLOOD`, `TISSUE`, `SWAB`, `URINE` (all four — the lab supports them) |
| `WorkflowStage` | the eight stages, constant names equal the wire names (`ACCESSIONED`, `READY_FOR_PROCESSING`, `PROCESSING`, `SEQUENCING`, `BIOINFORMATICS`, `QUALITY_CHECK`, `RESULT_READY`, `REPORT_GENERATED`); `displayName()`; `Optional<WorkflowStage> next()` (empty at `REPORT_GENERATED`) |
| `LabConfig` (record) | `baseUrl`, `apiKey` (`ApiKey`); `load(sysProps, env)` for unit-testable precedence |
| `ApiKey` | wraps the secret; `toString()` returns `***`; the raw value is reachable only by the request-spec factory |
| `LabMessages` | the lab's exact messages as constants, plus `invalidTransition(from, to)` |

_Lab quirk mapped in the records: `Sample.status` versus `Progress.currentStatus` (different field names on the wire for the same concept)._ Satisfies functional spec §2.4: stage order and `next()` are covered by `WorkflowStageTest`; typed access to lab answers by the records plus `LabJsonMappingTest`.

**JSON mapping (verified):** Jackson 2.19 reads records without annotations, but needs `jackson-datatype-jsr310` for `Instant` and a lenient mapper (`FAIL_ON_UNKNOWN_PROPERTIES=false`) because the lab's `POST /api/samples` adds a nested `order` object. One shared `ObjectMapper` (`LabJson`) is plugged into RestAssured per request spec.

### 2.4 Client Layers (`api-client`)

```
com.labflow.client
  LabClient                      facade: patients(), orders(), samples(); withApiKey(x), withoutApiKey() → new immutable LabClient
  http/LabRequestSpecs           builds the RestAssured request spec (base URI, JSON, Bearer key, log config with Authorization blacklisted)
  http/LabJson                   the shared ObjectMapper (JavaTimeModule, lenient)
  http/LabResponse<T>            statusCode(), header(name), Optional<T> body(), Optional<ApiError> error(), message(), describe()
  http/Rejection                 enum INVALID_INPUT(400), UNAUTHORIZED(401), NOT_FOUND(404), METHOD_NOT_ALLOWED(405), CONFLICT(409)
  api/PatientApi, OrderApi, SampleApi     one method per endpoint; return LabResponse<T>; never assert; take a body as Object (record, Map, or raw String for malformed JSON)
  service/PatientService, OrderService, SampleService   business verbs (below)
  assertions/LabAssertions, LabResponseAssert           custom AssertJ assertions
  preflight/LabPreflight                                the one-off reachability + key check (2.6)
```

**Service verbs (illustrative):** `PatientService.register(name)` / `find(id)`; `OrderService.place(patientId)` / `find(id)`; `SampleService.register(patientId, type[, orderId])`, `find`, `listFor(patientId)`, `moveTo(sampleId, stage)`, `walkTo(sampleId, stage)`, `progress`, `result`, `report`. Every operation that has a negative scenario also has an `attempt…` twin taking raw input (for example `attemptRegister(patientId, "PLASMA")`, `attemptMoveTo(sampleId, "NOPE")`) that never throws.

- **Happy methods** return domain objects and throw an `AssertionError` subclass on an unexpected status, with a message such as `POST /api/samples expected 201 but the lab answered 409 "Order does not belong…"`.
- **`LabResponse.describe()`** is one line (`POST /api/samples -> 409 "message"`) and never includes headers, request bodies or names.
- **Assertions:** `assertThat(resp).isRejectedAs(Rejection.CONFLICT, LabMessages.DUPLICATE_SAMPLE_TYPE)`, `.hasHeader("WWW-Authenticate", "Bearer")`, `.hasHeaderContaining("Allow", "GET")`, `.hasNoLocationHeader()`. The status is checked inside `isRejectedAs`, so tests speak in "kind of rejection", as the functional spec does.
- **"Nothing created or changed":** for samples, a follow-up read (`samples.listFor(patientId)` filtered client-side, never a global count) and an order's `samples` list; for refused patients/orders (no list endpoint exists) the proxy is "no `Location` header and no identifier in the body".
- **Alternative rejected:** a `LabRejectedException` plus `assertThatThrownBy` — simpler in the client, but header checks and follow-up assertions get awkward.
- **RestAssured types stay out of `api-tests`.** RestAssured reaches `api-tests` transitively, so this is a convention enforced by a small guard test (`NoRawHttpInChecksTest`, scans `api-tests/src/test/java` for `io.restassured`), satisfying functional spec §2.2 AC3/AC4. RestAssured static defaults are never used — per-request specs only — so there is no static mutable state and later parallelism stays safe.

The lab contract consumed is the one in `context/spec/002-mock-lab-rest-api/technical-considerations.md` §2.3 (paths under `/api`, `Bearer` key, exact messages, `GET /health` open).

### 2.5 Configuration

| Setting | Sources (highest first) | Default |
|---|---|---|
| Lab address | `-DbaseUrl=…` system property → `BASE_URL` env var | `http://localhost:8080` (blank counts as unset; trailing `/` stripped) |
| Access key | `API_TOKEN` env var **only** (no system property) | `mock-lab-local-token` (the lab's documented local default — not a secret) |

`-D` system properties reach the forked test JVM (verified), and environment variables are inherited. **Why env only for the key (decided):** a key passed as `-DapiToken=…` is written into Surefire's XML report (`<properties>`, verified) and is visible in the process list. `LabClient.withApiKey("wrong")` is a runtime override for the authentication scenarios, not a configuration path. `BASE_URL` is a generic name an engineer may already have exported for another project; the pre-flight message names the address it actually used as the safety net.

**The key never reaches any output** — four layers: (1) `ApiKey.toString()` is `***`; (2) `LogConfig.blacklistHeader("Authorization")` in the spec factory (verified: log shows `Authorization=[ BLACKLISTED ]`); (3) `LabResponse.describe()` and every failure message omit headers; (4) RestAssured logging is off by default.

### 2.6 Fail-Fast Pre-Flight

A `LauncherSessionListener` in `api-tests` (`LabPreflightListener`, registered in `src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener`) calls `LabPreflight.check(config)` once before any test: `GET /health` (JDK `HttpClient`, 2 s connect timeout) then `GET /api/samples` with the key.

| Situation | Message (greppable banner `LAB PRE-FLIGHT FAILED:`) |
|---|---|
| Unreachable / not 200 | `… cannot reach the lab at <url>. Start it: cd mock-lab-app && ./mvnw spring-boot:run` |
| 401 | `… the lab rejected the access key (set the API_TOKEN environment variable)` |

Behaviour under Surefire (verified): zero tests run, `BUILD FAILURE`, exit code 1, the message appears once as our own `[ERROR]` line right after the `T E S T S` banner (and again inside Maven's fork-error summary, followed by Maven's own stack frames — Maven noise we cannot suppress). A JUnit extension in a base class was **rejected** (spiked: it errors once per class). The listener lives only in `api-tests`, so the `common` / `api-client` self-tests still run without a lab. After changing the services file, run `clean` (a stale `target/test-classes` keeps the old one).

### 2.7 Test Data, Isolation, Failure Clarity

- **Unique data:** `UniqueData.name(prefix)` returns `prefix + "-" + UUID` (pure, no static mutable state). Each check creates its own patient, order and sample through the services. `LabTest` (base class) holds only `final LabClient lab`. The `test-data` module stays absent; builders are Phase 2.
- **Order independence:** `junit-platform.properties` enables random method and class ordering (`MethodOrderer.Random`, `ClassOrderer.Random`); this actively proves independence. Repeatability (functional spec §2.7 AC3) is verified by running the command twice against the same lab.
- **Failure clarity:** (1) `@DisplayName` states the business scenario on every check; (2) `ScenarioNameExtension` (a `TestExecutionExceptionHandler`, ~15 lines, registered on `LabTest`) rethrows `AssertionFailedError` with a `[Scenario: <displayName>]` prefix, keeping expected/actual (spike: the scenario text renders at the top of the failure); (3) rejection failures always include the lab's own message; (4) happy-path failures come from the service-layer `AssertionError`.

### 2.8 Test Inventory (`api-tests`, package `com.labflow.tests`)

| Class | Checks | Functional criteria |
|---|---|---|
| `happy.PatientOrderSampleTest` | `registeredPatientCanBeLookedUp`, `placedOrderCanBeLookedUp`, `registeredSampleCanBeLookedUp`, `newSampleIsAccessioned` | §2.5 (patient, order, sample, initial status) |
| `happy.SampleProgressionTest` | `movesThroughAllSevenStagesOneAtATime` (uses `next()`), `resultAvailableAtResultReady`, `reportAvailableAtReportGenerated` | §2.5 (stages, result, report) |
| `happy.FullJourneyTest` | `wholeJourneyFromPatientToReport` | §2.5 (whole journey) |
| `rejection.InputValidationRejectionTest` | patient without name, sample without type, sample/order without patient ("Patient is required."), order/sample for an unknown patient ("Patient not found."), invalid sample type | §2.6 |
| `rejection.DuplicateSampleRejectionTest` | `secondSampleOfSameTypeRefused` (order still has one sample), `sampleOfOtherTypeAccepted` | §2.6 |
| `rejection.UnknownSampleRejectionTest` | `@ParameterizedTest` over get, move, progress, result, report | §2.6 |
| `rejection.AuthenticationRejectionTest` | `@ParameterizedTest` (no key, wrong key): "Unauthorized.", `WWW-Authenticate`, nothing created or changed | §2.6 |
| `rejection.InvalidTransitionRejectionTest` | `@ParameterizedTest` over skip, backward, repeat, from-final; status unchanged | §2.6 |
| `rejection.BadRequestRejectionTest` | `malformedBodyRefused` (sample count for the patient unchanged), `deleteSampleIsMethodNotAllowed` (`Allow` header) | §2.6 |
| `rejection.NotReadyRejectionTest` | result before Result Ready, report before Report Generated, report at Result Ready (boundary) | §2.6 |
| `NoRawHttpInChecksTest` | guard: no `io.restassured` in the checks | §2.2 |

`@ParameterizedTest` is used in exactly the three places above where it genuinely removes duplication; everything else is a plain `@Test` because setup and messages differ per case. No tags yet (Phase 2). The project brief's "Accessioned → Processing is valid" example is treated as a refusal (the lab accepts only the next stage).

**Framework self-tests** (run in the default command, need no lab, use a JDK `com.sun.net.httpserver` stub): `common`: `WorkflowStageTest`, `LabConfigTest` (precedence, defaults — next to the class it tests); `api-client`: `SecretRedactionTest` (`toString`, captured `log().all()` never contains the key), `LabPreflightTest` (unreachable, 503, 401, 200), `LabAssertionsTest` (failure text has scenario, expected/actual and the lab's message), `LabJsonMappingTest` (`Instant`, unknown fields, enums).

### 2.9 Component Breakdown (files)

```
pom.xml
mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
README.md                                  (CHANGED: "Running Tests" section only)
common/pom.xml
common/src/main/java/com/labflow/common/model/…        Patient, Order, SampleSummary, Sample, Progress, StageEntry, TestResult, Report, ApiError, SampleType, WorkflowStage
common/src/main/java/com/labflow/common/config/…       LabConfig, ApiKey
common/src/main/java/com/labflow/common/LabMessages.java
common/src/test/java/com/labflow/common/WorkflowStageTest.java, common/config/LabConfigTest.java
api-client/pom.xml
api-client/src/main/java/com/labflow/client/…          (tree in 2.4)
api-client/src/test/java/com/labflow/client/…          SecretRedactionTest, LabPreflightTest, LabAssertionsTest, LabJsonMappingTest
api-tests/pom.xml
api-tests/src/test/java/com/labflow/tests/support/…    LabTest, UniqueData, ScenarioNameExtension, LabPreflightListener
api-tests/src/test/java/com/labflow/tests/happy/…      (2.8)
api-tests/src/test/java/com/labflow/tests/rejection/…  (2.8)
api-tests/src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener
api-tests/src/test/resources/junit-platform.properties
```

Module dependencies: `common` — none (JUnit at test scope); `api-client` — `common`, `rest-assured` (compile), Jackson `databind` + `datatype-jsr310`, `assertj-core` (compile, for the custom assertions); `api-tests` — `api-client`, `common` (declared explicitly), `junit-jupiter`, `junit-platform-launcher` (test scope, to compile the listener), `assertj-core`.

---

## 3. Impact and Risk Analysis

**System Dependencies**
- Depends on the lab's spec-002 contract (paths, messages, statuses, headers). A wording change in the lab breaks checks by design (`LabMessages` is the single place to update).
- Adds the first framework code to the repo; `mock-lab-app` itself is unchanged.
- Later phases build on this shape: `ui-tests` and `test-data` become new sibling modules; CI runs `./mvnw -B -ntp -pl api-tests -am test` after starting the lab and polling `/health` (JDK 17 via `setup-java`; Surefire XML at `**/target/surefire-reports/TEST-*.xml`; the report step must tolerate zero XML files because a pre-flight abort produces none).
- Documentation to refresh after implementation: `architecture.md` (AssertJ, Jackson, env-only key, pre-flight, root wrapper, module/package naming).

**Potential Risks & Mitigations**

| Risk | Mitigation |
|---|---|
| Local JDK 26 vs target 17 (never run on a real JDK 17 here) | `--release 17` (verified); CI pins 17; Maven's own JVM warnings on JDK 26 are cosmetic (do not silence with flags that break JDK 17). |
| RestAssured/Groovy on JDK 26 | Spiked OK incl. GPath; per-request specs only. |
| Jackson + `Instant` / extra lab fields | `jsr310` + lenient mapper, guarded by `LabJsonMappingTest`; enum names must match the wire (loud failure otherwise). |
| Key leakage (logs, reports, process list) | Env-only key, `ApiKey` `***`, header blacklist, no headers in messages; `SecretRedactionTest`. |
| Shared lab state across checks / repeated runs | UUID data, never assert global counts, filter by patient; random order; two consecutive runs verified. |
| Flakiness | No sleeps; connect/read timeouts set; the lab is synchronous. |
| Pre-flight message repeated / wrapped in Maven noise | Greppable banner prefix; documented in the README as "the first `[ERROR]` after `T E S T S`". |
| `-pl api-tests` without `-am` fails on a clean machine | Only `-am` is documented. |
| Stale `~/.m2` sibling jars / stale ServiceLoader file | Never `install`; `clean` after changing the services file. |
| Generic `BASE_URL` already exported for another project | Pre-flight names the address it tried. |
| Default local key hard-coded in code | It is the lab's documented local default, overridable by `API_TOKEN`; not a secret. |
| Wrapper first run needs the network | Documented as required tools: Java 17+ runtime and internet access on the first run. |

**Assumptions recorded (not separately confirmed):** package/groupId naming above; `LauncherSessionListener` for the pre-flight; random test ordering enabled; parameterized tests in exactly three places; a guard test scanning for `io.restassured`; the `Location`-header proxy for "nothing created" on refused patients/orders; asserting `Allow` contains `GET` (its exact value to be confirmed against the live lab during implementation).

---

## 4. Testing Strategy

The checks *are* the deliverable, so "testing" means proving the framework itself is trustworthy.

- **Self-tests (no lab needed):** the `common` and `api-client` tests listed in 2.8 — precedence and defaults, secret redaction (including a captured `log().all()` against a stub server), pre-flight cases (unreachable / 503 / 401 / 200), assertion failure text, JSON mapping.
- **Workflow checks:** the 2.8 inventory against the live lab; every rejection check asserts kind, exact message, headers and unchanged state.
- **Command verification (manual evidence, kept for the completion report):** (1) `./mvnw clean test` with the lab running → all green, twice in a row against the same lab (repeatability); (2) `./mvnw -pl api-tests -am test`; (3) `-Dtest=<Class>#<method>` alone (independence); (4) `BASE_URL`/`-DbaseUrl` override; (5) lab stopped → one clear pre-flight message, exit code 1, zero checks run (`./mvnw test -DbaseUrl=http://localhost:1` is the quick way to demonstrate); (6) wrong `API_TOKEN` → "rejected the access key"; (7) grep the whole output and `target/surefire-reports` for the key value (must be absent).
- **Deliberate-failure demonstration:** temporarily change one expectation to show the failure output names the scenario, the expected value, the actual value and the lab's message (functional spec §2.7 AC1).
- **Regression gate:** the lab's own 618 tests are unaffected (separate module, run from `mock-lab-app/`).
