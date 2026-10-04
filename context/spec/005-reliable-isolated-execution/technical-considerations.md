# Technical Specification: Reliable, Isolated Runs with Settings and a Run Log

- **Functional Specification:** [functional-spec.md](./functional-spec.md)
- **Status:** In Review
- **Author(s):** Alex, with the `testing-expert` and `github-actions-cicd` specialists

---

## 1. High-Level Technical Approach

Everything stays inside the existing Maven reactor and the JUnit 5 Platform. No new framework, no new runner.

1. **Labels** are JUnit 5 tags behind small composed annotations (`@ApiCheck`, `@UiCheck`, `@Smoke`, `@Regression`). The base classes `LabTest` and `UiTest` carry `api`/`ui` and `regression`, so every check inherits them by construction. Subsets are picked with Surefire's standard `-Dgroups=<tag expression>`, with no profiles.
2. **Parallelism** uses JUnit's own parallel engine: classes and methods run concurrently in one forked JVM. A tiny custom strategy reads one option, `-DchecksAtOnce=N`, and defaults to the number of cores.
3. **Screen checks under parallel** get **one Playwright + Browser per worker thread**, instead of today's single shared one, because Playwright for Java is not thread-safe per `Playwright` instance. Each check still gets its own `BrowserContext` + `Page`.
4. **Test data** moves to a new **`test-data`** module of pure value recipes (no HTTP). It replaces both `UniqueData` copies and gives every generated name a fixed `TEST-` prefix.
5. **Settings**: `LabConfig` gains `ENVIRONMENT` (`local`|`ci`, which supplies only the default address). `ui-tests` gains `BROWSER` (`chromium`|`webkit`). Both use the existing precedence rule (`-D` property, then env var, then default) and are validated in the existing pre-flight listener, so a bad value stops the run before any check.
6. **Run log**: SLF4J + Logback, as already chosen in `architecture.md`.
   - A RestAssured `Filter` in `api-client` writes one summary line per request: method, path, status, duration and identifiers. It never writes the access key, the password or patient names.
   - The check name comes from MDC. Output goes to the console at INFO and to `target/logs/<module>.log` at DEBUG.
   - `-DlogBodies=true` adds masked bodies to the file.

Modules touched: root `pom.xml`, `common`, `api-client`, new `test-data`, `api-tests`, `ui-tests`, `README.md`, `architecture.md`. `mock-lab-app` is not touched.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1. Module and Dependency Changes

| Module | Change |
|---|---|
| root `pom.xml` | Add `test-data` to `<modules>` (after `common`) and to `dependencyManagement`. Manage `slf4j-api` (2.0.x) and `logback-classic` (current 1.5.x; Java 11+, fine on 17). The Surefire reporter block stays **byte-identical** because `PhrasedTestNamesGuardTest` reads it as text. No profiles, no `groups` default. |
| `common` | New package `com.labflow.common.junit` (labels, parallel strategy, check-name interceptor). Adds `junit-jupiter-api` and `junit-platform-engine` at **`provided`** scope: compile-time only, so they do not leak into consumers, which already bring JUnit at test scope. `common` keeps zero third-party *runtime* dependencies. |
| `test-data` (new) | Depends on `common` only. Main code: recipes plus `UniqueData`. Its own unit tests at test scope. |
| `api-client` | Adds `slf4j-api` (compile) for the request-log filter. Adds `logback-classic` (test) with a WARN-only `logback-test.xml` to avoid the "no SLF4J provider" warning. |
| `api-tests`, `ui-tests` | Add `test-data` (test scope) and `logback-classic` (test scope). Each gets `src/test/resources/logback-test.xml`. Delete the local `UniqueData` copies. |

**Assumption:** the labels, the parallel strategy and the check-name interceptor live in `common` rather than a new "test-support" module. That keeps them in one place for both test modules without adding a sixth module. The `provided` scope keeps `common` free of runtime dependencies.

### 2.2. Labels (Functional §2.1)

- **Annotations** live in `common/src/main/java/com/labflow/common/junit/`: `ApiCheck` (`@Tag("api")`), `UiCheck` (`@Tag("ui")`), `Smoke` (`@Tag("smoke")`), `Regression` (`@Tag("regression")`). Each has `@Retention(RUNTIME)`, `@Target({TYPE, METHOD})` and `@Inherited`. A typo becomes a compile error, not a silently empty run.
- **By construction:**
  - `LabTest` carries `@ApiCheck @Regression`.
  - `UiTest` carries `@UiCheck @Regression`.
  - The existing wiring guards already require every check to extend one of them, so "every check is api|ui and regression" needs no per-file tags.
  - The `// @regression` comment pseudo-tags are removed.
- **Smoke set**, with `@Smoke` at method level (6 checks):

  | Module | Check |
  |---|---|
  | api | `FullJourneyTest#wholeJourneyFromPatientToReport` |
  | api | `PatientOrderSampleTest`: the "registered sample starts Accessioned" check |
  | api | `AuthenticationRejectionTest#readWithoutKeyIsRefused` |
  | ui | `SignInTest#validSignInOpensSampleList` |
  | ui | `SampleRegistrationTest#registeredSampleIsListedAsAccessioned` |
  | ui | `ReportJourneyTest#fullJourneyThroughScreensEndsInReport` |

- **Self-checks** (unit and guard tests, which need no lab) stay **untagged**. "Untagged" is expressed as `!(api | ui)`.
  **Assumption:** this is chosen over tagging ~40 self-check classes with a `self-check` tag. Less churn, and the wiring guards already make it impossible for a real check to be untagged.

| Purpose | Command |
|---|---|
| smoke (+ self-checks) | `./mvnw test -Dgroups="smoke | !(api | ui)"` |
| regression (all checks) | `./mvnw test -Dgroups=regression` |
| workflow checks only | `./mvnw -pl api-tests -am test -Dgroups=api` |
| screen checks only | `./mvnw -pl ui-tests -am test -Dgroups=ui` |
| everything (unchanged) | `./mvnw clean test` |

- **Zero matches:**
  - Surefire's `failIfNoTests` defaults to false, and `surefire.failIfNoSpecifiedTests` applies to `-Dtest`, not `groups`. So an unknown label gives `Tests run: 0` and a green build, which is what the spec asks for.
  - For an unmistakable line, a `CheckCountListener` (a JUnit `TestExecutionListener`, registered by ServiceLoader in api-tests and ui-tests next to the pre-flight listener) logs `Lab checks run: N (labels: <expr or "all">)` in `testPlanExecutionFinished`. "Checks" counts tests tagged `api` or `ui`.
- **Guard** `LabelGuardTest`, one per test module:
  - The base class carries the right annotations.
  - The set of `@Smoke` methods equals the allowlist above, found by reflection over the check packages, so a change to the smoke set is deliberate.
  - The source contains no raw `@Tag(` in check files, so all labels go through the annotations.

### 2.3. One Check, Many Inputs (Functional §2.2)

Convert copy-pasted variants to `@ParameterizedTest` with a `name = "..."` template, or with `Arguments.argumentSet("plain-language name", ...)` (available in JUnit 5.11+, so in the 5.14 line). Each row is then reported under a business name. The reporters already print parameterized rows by display name (spec 004).

| File | Today | After |
|---|---|---|
| api `InputValidationRejectionTest` | `patientWithoutNameIsRefused` loops over `null`/`""`/`"   "` in one `@Test`. `sampleWithoutType`, `sampleWithoutPatient`, `sampleForUnknownPatient` and `unsupportedSampleType` repeat the same steps. `orderWithoutPatient` and `orderForUnknownPatient` repeat steps. | three parameterized checks: patient name, sample input, order input |
| api `BadRequestRejectionTest` | two malformed-body tests | one table of bodies |
| api `DuplicateSampleRejectionTest` | two tests that differ by sample type | `@EnumSource`-style table |
| ui `SampleRegistrationRejectionTest` | `unknownPatientIsRefused` and `unsupportedSampleTypeIsRefused` | one table of (input, expected message) |

Rows that need a patient created *inside* the check take a recipe *description*, not a created value, so the table stays static and each row creates its own data.

### 2.4. Parallel Execution (Functional §2.3)

**`junit-platform.properties`** in both `api-tests` and `ui-tests` (added to the existing files):

| Key | Value |
|---|---|
| `junit.jupiter.execution.parallel.enabled` | `true` |
| `junit.jupiter.execution.parallel.mode.default` | `concurrent` |
| `junit.jupiter.execution.parallel.mode.classes.default` | `concurrent` |
| `junit.jupiter.execution.parallel.config.strategy` | `custom` |
| `junit.jupiter.execution.parallel.config.custom.class` | `com.labflow.common.junit.ChecksAtOnceStrategy` |

The random-order and timeout keys stay. Random order still applies within each level, and `CheckIndependenceGuardTest` keeps passing.

**`ChecksAtOnceStrategy`** (`ParallelExecutionConfigurationStrategy`, about 20 lines, in `common`):
- It reads `checksAtOnce` from JUnit's `ConfigurationParameters`, then the `checksAtOnce` system property. *Corrected during Slice 6:* JUnit hands a custom strategy parameters prefixed with `junit.jupiter.execution.parallel.config.`, so the short `-DchecksAtOnce` never reaches it through them. The strategy therefore reads the system property directly; Surefire forwards `-D` to the fork, just like `-DbaseUrl` today. The long JUnit key wins if both are set.
- Unset means `Runtime.availableProcessors()`. Anything other than a positive integer fails the run with `checksAtOnce must be a positive whole number, got '<value>'`.
- It returns parallelism = core pool = max pool = N, with a saturate predicate that returns `true`. This stops the ForkJoinPool from adding compensation threads, so "no more than N at once" holds.
- `-DchecksAtOnce=1` therefore runs the checks one at a time.

**Assumption:** a custom strategy is chosen over the built-in ones. `fixed` cannot default to the core count, and `dynamic` cannot take an explicit N, so neither meets the spec alone. Exposing JUnit's raw property names to the engineer is the rejected alternative.

**Surefire:** keep `forkCount=1` and `reuseForks=true` (the defaults). All parallelism happens inside the one JVM. Maven `-T` is not used, because it interleaves output and duplicates setup.

**Shared state audit:**
- `LabClient` is immutable and builds a fresh `RequestSpecification` per request with no RestAssured statics.
- `LabJson`'s mapper is static final and read-only after configuration.
- There are no other statics in the check code. The only exception is `RecordingPauseTest`, a self-test with its own static Playwright: it gets `@Isolated` so it never runs beside screen checks.
- No `@ResourceLock` is needed for checks, because all data is unique per check.

### 2.5. Screen Checks Under Parallel (Functional §2.3, §2.5)

The changes are in `ui-tests/.../support/BrowserExtension.java`:

- **One `BrowserHandle` (Playwright + Browser) per JUnit worker thread**, held in a `ThreadLocal` and created lazily on that thread's first screen check.
- Every handle is also registered in one root-store `CloseableResource` (a synchronized list). All browsers therefore close once, after the last check, from the closing thread, when nothing else is using them.
- The number of browsers is at most `checksAtOnce`.
- *Rejected alternatives:*
  - A Playwright per check would start a driver process for every check, about 1–2 s each, which defeats the speed-up.
  - One shared Playwright is today's design and is not thread-safe.
- **Fresh `BrowserContext` + `Page` per check** is unchanged. That is the "own separate browser window" in the spec.
- **`SEPARATE_THREAD` timeout:**
  - The check body runs on a helper thread while the worker thread blocks waiting for it. So each `Playwright` is still used by one thread at a time. This is the same arrangement spec 004 already runs green.
  - A check abandoned at its 2-minute limit can still touch its own thread's handle. That noise is now limited to the one worker thread instead of the whole run.
- **Browser choice:**
  - Enum `BrowserChoice { CHROMIUM, WEBKIT }` in `ui-tests/.../config/`, loaded case-insensitively from `-Dbrowser`, then `BROWSER`, then `chromium`.
  - `BrowserExtension` maps it to `playwright.chromium()` / `.webkit()`.
  - An unknown value throws `Unsupported browser 'x'. Supported: chromium, webkit` from `UiPreflightListener`, before any check.
  - The launch-failure message names the chosen browser and its install command.
- **Firefox is not supported** (FS Change Log 2026-10-04): Playwright's Firefox 155.0 does not start on macOS 27 ("Could not find profile folder"), even outside the framework.
- **Install:** `./mvnw -N exec:java -Dexec.args="install webkit"` once, and on Linux `--with-deps`. The default `install chromium` is unchanged.
- **Failure artifacts** become `target/ui-failures/<browser>/<TestClass>/<scenario>/`. Names are unique per check already (class + display name + row index), so parallel runs cannot collide. `ArtifactNamesTest` and `BrowserExtensionArtifactsTest` are updated for the extra level.

### 2.6. Test-Data Recipes (Functional §2.4)

`test-data/src/main/java/com/labflow/testdata/`:

| Type | Responsibility |
|---|---|
| `UniqueData` | The single generator: `name(String kind)` returns `TEST-<kind>-<uuid>`. `TEST_PREFIX = "TEST-"`. It replaces both copies. |
| `PatientDraft` (record) + `Patients` | `Patients.valid()` gives a unique `TEST-patient-…` name. Variations: `withName(String)`, `withoutName()` (null), `blankName()`. |
| `SampleDraft` (record) + `Samples` | `Samples.validFor(patientId)` gives a supported type (BLOOD) and no order. Variations: `ofType(SampleType)`, `withRawType(String)` for unsupported types, `withoutPatient()`, `withoutType()`, `inOrder(orderId)`. |
| `OrderDraft` (record) + `Orders` | `Orders.validFor(patientId)`. Variations: `withoutPatient()`. |

- Recipes are **plain values**: they never call the lab. A check passes the draft's fields to the existing service methods, for example `lab.patients().register(patient.name())` or `lab.samples().attemptRegister(s.patientId(), s.rawType())`. `api-client` signatures stay unchanged, and `test-data` stays free of HTTP.
- *Rejected alternative:* recipes that call `LabClient` and return created records read slightly nicer but couple the data module to the client.
- Base-class convenience: `LabTest` and `UiTest` get `newPatient()` (register `Patients.valid()` and return the `Patient`). This replaces inline `register(UniqueData.name(...))` calls.
- Guards:
  - `CheckIndependenceGuardTest`'s `UniqueData` check is pointed at `test-data`.
  - A new rule in the same guard: check files contain no `UUID.randomUUID` and no string literal starting `"ui-patient"`/`"patient-"`, so data comes only from recipes.
  - `test-data`'s own unit test asserts uniqueness and the `TEST-` prefix.

### 2.7. Settings (Functional §2.5)

| Setting | Source, in precedence order | Default | Secret? |
|---|---|---|---|
| Lab address | `-DbaseUrl` → `BASE_URL` → the environment's address | via `local` | no |
| Access key | `API_TOKEN` env only (unchanged) | `mock-lab-local-token` | yes (`***`) |
| Environment | `-Denvironment` → `ENVIRONMENT` | `local` | no |
| Browser | `-Dbrowser` → `BROWSER` | `chromium` | no |
| Checks at once | `-DchecksAtOnce` | number of cores | no |
| Request/answer bodies in log | `-DlogBodies` → `LOG_BODIES` | `false` | no |

- `LabEnvironment` enum in `common/.../config/`: `LOCAL` and `CI`, both `http://localhost:8080`. In Phase 3 the CI job starts the lab on the runner; only the name differs. `LabConfig` becomes `(baseUrl, apiKey, environment)` and `load(...)` throws `Unknown environment 'x'. Known: local, ci`.
- **Run banner:** the pre-flight listeners log once, at INFO, `Lab run: environment=local address=http://localhost:8080 key=*** [browser=chromium] checksAtOnce=8`. They validate the settings first, then probe the lab as today.
- **Assumption:** the pre-flight lab probe stays unconditional. A run with a label that matches nothing therefore still needs a running lab. The spec's criteria assume a running lab, and making the probe tag-aware adds complexity for no requirement.
- Codespaces/devcontainers sometimes set `BROWSER` to a helper path. The unsupported-browser error catches this, and the README mentions it.

### 2.8. Run Log (Functional §2.6)

- **`RequestLogFilter`** (`api-client/.../http/`, a RestAssured `Filter`) is added in `LabRequestSpecs.create`.
  - It writes one INFO line per request on logger `lab.http`:
    `POST /api/samples -> 201 in 34 ms [patient=P-… sample=S-…]`
  - Identifiers come from the path and from the response JSON fields `id`, `patientId`, `orderId` and `sampleId`, read with `LabJson`.
  - The query string is not logged.
  - Headers are never logged at all, which goes further than the existing `Authorization` blacklist.
- **Bodies:** only when `logBodies=true`, at DEBUG, so they go to the file and not the console.
  - Before writing, `LogMasker` (a pure class, unit-tested) parses the body and replaces the values of `name` and `password` at any depth with `***`.
  - A body that is not JSON (for example the malformed-body checks) is logged only as `<non-JSON body, N chars>`.
- **Check name in every line:**
  - `CheckNameInterceptor` (`common/.../junit/`, an `InvocationInterceptor`) puts `check=<Class> › <display name>` into the SLF4J MDC around `interceptTestMethod`/`interceptTestTemplateMethod` and the `beforeEach`/`afterEach` methods, then clears it.
  - JUnit's timeout interceptor wraps user interceptors, so this code runs **on the thread that actually executes the body**, including under `SEPARATE_THREAD`.
  - It is registered on `LabTest` and `UiTest`.
  - MDC is thread-local, so it stays correct under parallel execution.
  - **Verify during implementation** with a test that logs from a timed check.
- **Logback** (`api-tests` / `ui-tests` `src/test/resources/logback-test.xml`):
  - Console appender at INFO, pattern `%d{HH:mm:ss.SSS} [%thread] %X{check} %msg`.
  - File appender at DEBUG to `target/logs/api-tests.log` / `target/logs/ui-tests.log`, with `append=false`. The path is relative, and Surefire's working directory is the module, so `clean` removes it.
  - The thread name in the pattern is also the cheap proof of concurrency (§4).
- **No console printing:**
  - The framework has no `System.out` / `System.err` / `printStackTrace` today.
  - A new `NoConsolePrintingGuardTest` (in `api-tests`, scanning `common`, `api-client`, `test-data`, `api-tests` and `ui-tests` sources the way `PhrasedTestNamesGuardTest` reads `../pom.xml`) keeps it that way. `mock-lab-app` is excluded.
- **Screen checks:** Page Objects do not log typed values. The password is already kept out of traces by `RecordingPause` (spec 004). Data preparation in screen checks goes through `api-client`, so it is logged the same way.

---

## 3. Impact and Risk Analysis

- **System Dependencies:**
  - The lab API is unchanged.
  - `PhrasedTestNamesGuardTest`, `CheckIndependenceGuardTest`, both wiring guards, `ArtifactNamesTest` and `BrowserExtensionArtifactsTest` need edits. These are listed above.
  - `architecture.md` needs updating for:
    - the `test-data` module
    - `common`'s provided JUnit dependencies
    - `slf4j-api` in `api-client`
    - per-thread browsers
    - the new settings
  - Phase 3 CI benefits directly:
    - Jobs become `-pl api-tests -am test -Dgroups=api` and `-pl ui-tests -am test -Dgroups=ui -Dbrowser=chromium`.
    - Jobs upload `target/surefire-reports` and `target/logs`.

| Risk | Mitigation |
|---|---|
| A hidden dependency between checks shows up only under parallel execution (for example a list assertion that sees another check's samples) | Checks never assert global counts (spec 003 rule). Grep list/size assertions during implementation. Prove with 3 consecutive parallel runs on one lab. |
| Playwright misuse across threads (abandoned timed-out body, cross-thread close) | One handle per worker thread. Close only at the end of the run. The timeout path only affects its own thread's handle. Fallback, if flaky: `mode.default=same_thread` in `ui-tests` (classes still parallel). |
| N browsers at once (N = cores) uses a lot of memory on small machines | Document `-DchecksAtOnce=2` in the README for laptops and CI runners. |
| ForkJoinPool work-stealing runs a second check on a blocked worker thread, which would share that thread's Playwright | The worker blocks in the timeout's `Future.get`, not in a ForkJoin join, so it does not steal. `saturate=true` and max pool = N prevent extra threads. Verify with the thread names in the log. |
| MDC lost on the timeout thread, so lines have no check name | Interceptor-based MDC (§2.8), with a dedicated test. |
| A secret or patient name leaks through a new log line | Headers are never logged. Bodies are off by default and masked when on. Names are never taken into identifier extraction. `LogMasker` unit tests. `RunLogSecrecyTest`. A grep of `target/logs`, `surefire-reports` and console output after a `-DlogBodies=true` full run. |
| Shell quoting of tag expressions (`|`, `!`) | Quote them in the README and in Phase 3 YAML. |
| The Surefire reporter block is edited by accident | `PhrasedTestNamesGuardTest` already fails if it changes. |

---

## 4. Testing Strategy

- **Unit (no lab), as self-checks:**
  - `ChecksAtOnceStrategy`: default = cores, explicit N, bad value.
  - `LabConfig`: environment precedence, unknown environment, explicit address beats the environment.
  - `BrowserChoice`: case-insensitive, unknown value message.
  - `UniqueData` / recipes: uniqueness, `TEST-` prefix, valid defaults.
  - `LogMasker`: `name`/`password` at any depth, non-JSON body.
  - `RequestLogFilter` against a canned response: line format, identifiers, no headers.
  - `CheckNameInterceptor`: MDC set on the body thread under `SEPARATE_THREAD`, via the existing `junit-platform-testkit`.
- **Guards:**
  - `LabelGuardTest`: base labels, the smoke allowlist, no raw `@Tag`.
  - `NoConsolePrintingGuardTest`.
  - The updated `CheckIndependenceGuardTest` (recipes only).
- **Lab-backed:** `RunLogSecrecyTest` (`@ApiCheck`) registers a patient with a recipe name, makes a few calls, and asserts that the log file contains its sample identifier and contains neither the patient name nor the access key.
- **Acceptance proofs**, recorded in the README and the verify report:
  1. Run each documented label command once and record the `Tests run` totals and the `Lab checks run: N` line. Also run `-Dgroups=nosuch` and record `Lab checks run: 0`.
  2. Run `./mvnw test -Dgroups=regression` three times in a row on one lab with the default settings: all green.
  3. Timing: one warm-up run, then the median of 3 for the default and for `-DchecksAtOnce=1`, using Maven's `Total time:` line. Record the core count. Time smoke the same way, for information only (FS Change Log: the smoke-vs-regression time criterion was removed).
  4. Concurrency cap: with `-DchecksAtOnce=2`, the distinct `[thread]` names on check lines in `target/logs/*.log` number at most 2. With the default, there is more than 1.
  5. Run `-Dbrowser=webkit` once, all screen checks green. Check the unknown browser and unknown environment messages.
  6. After a `-DlogBodies=true` full run, grep for the key, the password and every `TEST-patient-` name across `target/logs`, `target/surefire-reports` and console output: 0 hits.
