# Tasks: Reliable, Isolated Runs with Settings and a Run Log (Phase 2)

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

After each slice:
- The project still builds and runs with `./mvnw` from the repository root.
- One more piece of the functional spec works end to end.
- Every existing check (`common`, `api-client`, `api-tests`, `ui-tests`) stays green.
- Nothing under `mock-lab-app/` changes.

**Verification conventions (used by every Verify task):**

- **Lab on a spare port.** The framework needs a running lab but never starts one. A Verify task starts its own lab on a spare port, so a lab the engineer may be running on 8080 is never disturbed:
  1. Build the jar if it is missing: `cd mock-lab-app && ./mvnw -q -B clean package -DskipTests`.
  2. Start it: `java -jar mock-lab-app/target/mock-lab-app.jar --server.port=18080 &`.
  3. Wait until it is up: `curl -sf --retry 30 --retry-delay 1 --retry-connrefused http://localhost:18080/health`.
  4. Point the checks at it with `-DbaseUrl=http://localhost:18080`.
  5. At the end, stop every process you started. Never touch anything listening on 8080.
- **Maven.**
  - Run from the repo root through `./mvnw`; `mvn` is not on the PATH.
  - Never use `-q` when reading the "Tests run" summary. Never run `install`.
  - The JDK is 26 and the code targets 17. Ignore the harmless `WARNING:` lines this produces.
  - Quote tag expressions, for example `-Dgroups="smoke | !(api | ui)"`.
- **Bounded runs.** There is no `timeout` binary on this machine.
  - Run long Maven or Playwright commands in the background with an explicit wall-clock limit, for example `( cmd ) & pid=$!; ( sleep 900; kill $pid ) &`, or use the tool's own timeout.
  - Never leave a run hanging.
  - Delete only the scratch files you created yourself.
- **Browsers.** Chromium for Playwright 1.63.0 is already installed. WebKit is installed in Slice 1 with `./mvnw -N exec:java -Dexec.args="install webkit"`. Firefox is not supported (it does not start on this macOS 27 machine; see the FS Change Log).
- **Agent split** (same rule as specs 003 and 004):
  - `general-purpose` writes new main code (`common`, `test-data`, `api-client`, `ui-tests/src/main`), together with its unit tests, proven by RED validation.
  - `testing-expert` writes the checks, test-side support, guards and every Verify task.
  - `github-actions-cicd` owns POM wiring.
- **Evidence.**
  - Every task that adds tests proves them with RED validation: break the behaviour, watch the test fail, restore the code exactly, watch it pass.
  - Every Verify task quotes real command output.
- **Guards to keep intact.**
  - `PhrasedTestNamesGuardTest` reads the root Surefire reporter block as text. Never edit that block.
  - `CheckIndependenceGuardTest` forbids `@BeforeAll`, `@AfterAll`, `@TestInstance` and ordering annotations in check files.
- **Artifacts.**
  - Verify tasks delete the screenshots, traces, `ui-tests/target/ui-failures/` contents and scratch scripts they produced.
  - The Feature Testing & Regression slice keeps its tests.

---

- [x] **Slice 1: Choose Environment and Browser from Outside, with a Run Banner**

  > A run can be pointed at the `local` or `ci` environment and run its screen checks in Chromium or WebKit without editing anything. Every run logs one settings banner with the key hidden. Unknown values stop the run before any check. (FS §2.5)

  - [x] Task: Root `pom.xml`: manage `slf4j-api` (2.0.x) and `logback-classic` (latest 1.5.x on Maven Central; record the version chosen). Add `slf4j-api` (compile) to `common`. Add `logback-classic` (test) to `api-tests` and `ui-tests`, each with a minimal `src/test/resources/logback-test.xml` (console, INFO, pattern `%d{HH:mm:ss.SSS} [%thread] %msg%n`). Add `logback-classic` (test) to `common` and `api-client` with a WARN-only console config, so no "no SLF4J provider" warning appears. Leave the Surefire reporter block untouched. Install Firefox and WebKit once with `./mvnw -N exec:java -Dexec.args="install firefox webkit"`. **[Agent: github-actions-cicd]**
  - [x] Task: Add the environment to the settings.
    - `common/.../config/LabEnvironment` enum: `LOCAL` and `CI`, each with a default address (both `http://localhost:8080`), parsed case-insensitively.
    - `LabConfig` becomes `(baseUrl, apiKey, environment)`. The environment comes from `-Denvironment`, then `ENVIRONMENT`, then `local`; blank counts as unset. The address comes from `-DbaseUrl`, then `BASE_URL`, then the environment's default.
    - An unknown environment throws `Unknown environment 'x'. Known: local, ci`. `toString()` still masks the key.
    - Extend `LabConfigTest`: precedence, explicit address beats environment, unknown value message.
    - RED-validate.
    - **[Agent: general-purpose]**
  - [x] Task: Add the browser choice to the screen checks.
    - `ui-tests/src/main/.../config/BrowserChoice` enum: `CHROMIUM`, `WEBKIT` (`FIREFOX` was built first, then removed, see the FS Change Log). It is loaded from `-Dbrowser`, then `BROWSER`, then `chromium`, case-insensitively.
    - An unknown value throws `Unsupported browser 'x'. Supported: chromium, webkit`.
    - Unit tests plus RED validation.
    - `BrowserExtension` launches the chosen type. The launch-failure message names that browser and its install command.
    - **[Agent: general-purpose]**
  - [x] Task: Remove Firefox (FS Change Log 2026-10-04). Drop `FIREFOX` from `BrowserChoice` and its `switch` case in `BrowserExtension`. The unknown-value message becomes `Unsupported browser 'x'. Supported: chromium, webkit`, and `-Dbrowser=firefox` is now rejected with that message. Update `BrowserChoiceTest` and any Javadoc. RED-validate the rejection of `firefox`. **[Agent: general-purpose]**
  - [x] Task: Validate the settings and log the banner in the pre-flight listeners.
    - `LabPreflightListener` and `UiPreflightListener` resolve and validate the settings first, so a bad value stops the run before any check.
    - They then log one INFO line through SLF4J: `Lab run: environment=<e> address=<url> key=*** [browser=<b>]` (the browser only in ui-tests).
    - After that they probe the lab as today.
    - Update `LabRunWiringTest` / `UiRunWiringGuardTest` if they assert on listener behaviour.
    - **[Agent: testing-expert]**
  - [x] Verify:
    - Start the lab on 18080.
    - (a) Run `./mvnw -pl api-tests -am test -DbaseUrl=http://localhost:18080 -Denvironment=ci`. Expect green, and quote the banner showing `environment=ci` and `key=***`.
    - (b) Run with `-Denvironment=nope` and with `-Dbrowser=opera` (ui-tests). Each must stop before any check with the exact message.
    - (c) Run `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080` once with `-Dbrowser=webkit`. Expect all screen checks green; quote the `Tests run` lines.
    - (d) Grep the console output and `surefire-reports` for `mock-lab-local-token`: 0 hits.
    - Stop the lab and delete scratch files.
    - **[Agent: testing-expert]**

- [x] **Slice 2: One Shared Set of Test-Data Recipes**

  > Patients, orders and samples for every check come from one `test-data` module with valid defaults. Every generated name starts with `TEST-`. (FS §2.4)

  - [x] Task: Create the `test-data` module (depends on `common` only).
    - Add it to the root `<modules>` after `common` and to `dependencyManagement`.
    - Add it at test scope to `api-tests` and `ui-tests`.
    - Add `junit-jupiter` (test) for its own unit tests.
    - **[Agent: github-actions-cicd]**
  - [x] Task: Write the recipes in `test-data/src/main/java/com/labflow/testdata/`.
    - `UniqueData.name(kind)` returns `TEST-<kind>-<uuid>`, with `TEST_PREFIX`.
    - `PatientDraft` + `Patients.valid()`, with `withName`, `withoutName`, `blankName`.
    - `OrderDraft` + `Orders.validFor(patientId)`, with `withoutPatient`.
    - `SampleDraft` + `Samples.validFor(patientId)` (BLOOD, no order), with `ofType`, `withRawType`, `withoutPatient`, `withoutType`, `inOrder`.
    - Records are immutable and every `with…` returns a copy.
    - Unit tests: uniqueness, prefix, valid defaults, each variation.
    - RED-validate.
    - **[Agent: general-purpose]**
  - [x] Task: Move every check to the recipes.
    - Add `newPatient()` to `LabTest` and update `UiTest.newPatient()` to use `Patients.valid()`.
    - Replace every inline `UniqueData.name(...)` and hand-written name or raw value in `happy/` and `rejection/` in both modules with recipes.
    - Delete both local `UniqueData` copies.
    - Point `CheckIndependenceGuardTest` (both copies) at the `test-data` `UniqueData`.
    - Add a rule to that guard: check files contain no `UUID.randomUUID` and no hand-written name prefixes such as `"ui-patient"` / `"patient-"`.
    - RED-validate the new rule.
    - **[Agent: testing-expert]**
  - [x] Verify:
    - Start the lab on 18080.
    - Run `./mvnw clean test -DbaseUrl=http://localhost:18080 -fae`. Expect green; quote the per-module `Tests run` lines, including `test-data`.
    - The lab has no patient list (`GET /api/patients` is 405), so prove the `TEST-` names directly: register one patient the way `LabTest.newPatient()` does (a `Patients.valid()` name), then `curl` `GET /api/patients/<id>` and show the name starts with `TEST-`. Also quote the green `test-data` unit tests.
    - `grep -rn "UniqueData" api-tests/src ui-tests/src` shows only imports from `com.labflow.testdata`.
    - Stop the lab and delete scratch files.
    - **[Agent: testing-expert]**

- [ ] **Slice 3: Labels and One Command per Subset**

  > Every check is labelled `api` or `ui`, plus `regression`. Six checks are labelled `smoke`. Smoke, regression, api-only and ui-only runs each have one command. A run whose label matches nothing says `Lab checks run: 0`. (FS §2.1)

  - [ ] Task: Composed label annotations in `common/src/main/java/com/labflow/common/junit/`: `ApiCheck`, `UiCheck`, `Smoke`, `Regression`.
    - Each is meta-annotated with `@Tag`, `@Retention(RUNTIME)`, `@Target({TYPE, METHOD})` and `@Inherited`.
    - Add `junit-jupiter-api` at `provided` scope to `common` (coordinate the POM line with `github-actions-cicd` conventions; no version, use the BOM).
    - Unit test: each annotation resolves to its tag.
    - **[Agent: general-purpose]**
  - [ ] Task: Apply the labels and count the checks.
    - `LabTest` gets `@ApiCheck @Regression`; `UiTest` gets `@UiCheck @Regression`.
    - Add `@Smoke` to exactly these six:
      - api `FullJourneyTest#wholeJourneyFromPatientToReport`
      - api `PatientOrderSampleTest`'s "registered sample starts Accessioned" check
      - api `AuthenticationRejectionTest#readWithoutKeyIsRefused`
      - ui `SignInTest#validSignInOpensSampleList`
      - ui `SampleRegistrationTest#registeredSampleIsListedAsAccessioned`
      - ui `ReportJourneyTest#fullJourneyThroughScreensEndsInReport`
    - Remove the `// @regression` comment pseudo-tags.
    - Add `CheckCountListener` (a `TestExecutionListener`, registered through `META-INF/services` in both modules). In `testPlanExecutionFinished` it logs `Lab checks run: N (labels: <groups or "all">)`, where N counts executed tests tagged `api` or `ui`.
    - Add `LabelGuardTest` in each module:
      - the base class carries the right labels;
      - the `@Smoke` methods found by reflection equal the allowlist;
      - no raw `@Tag(` appears in check files.
    - RED-validate the guard.
    - **[Agent: testing-expert]**
  - [ ] Verify:
    - Start the lab on 18080.
    - Run each of these with `-DbaseUrl=http://localhost:18080` and quote the `Tests run` lines and the `Lab checks run:` lines:
      - `./mvnw test -Dgroups="smoke | !(api | ui)"`: 6 lab checks plus the self-checks.
      - `./mvnw test -Dgroups=regression`: all lab checks.
      - `./mvnw -pl api-tests -am test -Dgroups=api`
      - `./mvnw -pl ui-tests -am test -Dgroups=ui`
      - `./mvnw test -Dgroups=nosuch`: build success and `Lab checks run: 0`.
    - Record the smoke and regression `Total time:` lines.
    - Stop the lab.
    - **[Agent: testing-expert]**

- [ ] **Slice 4: One Check, Many Inputs**

  > Checks that differ only by input are written once with a table, and each row appears under its own plain-language name. (FS §2.2)

  - [ ] Task: Convert the copy-pasted variants to `@ParameterizedTest`, using `name = "..."` or `Arguments.argumentSet("plain name", ...)`:
    - api `InputValidationRejectionTest`: one check for patient name (null / empty / blank, replacing the in-test loop), one for sample input (missing patient, unknown patient, missing type, unsupported type), and one for order input.
    - api `BadRequestRejectionTest`: malformed bodies.
    - api `DuplicateSampleRejectionTest`: sample types.
    - ui `SampleRegistrationRejectionTest`: unknown patient / unsupported type, each with its expected message.
    - Each row creates its own data from recipes.
    - Keep every assertion that exists today; nothing may get weaker.
    - RED-validate one row per converted check: a failure must name the failing row.
    - **[Agent: testing-expert]**
  - [ ] Verify:
    - Start the lab on 18080.
    - Run `./mvnw clean test -DbaseUrl=http://localhost:18080 -fae`. Expect green.
    - In `target/surefire-reports/TEST-*InputValidationRejectionTest.xml` and `TEST-*SampleRegistrationRejectionTest.xml`, quote that every row is listed under its plain-language name.
    - Confirm the total number of executed cases is not lower than before the conversion; quote both counts.
    - Stop the lab.
    - **[Agent: testing-expert]**

- [ ] **Slice 5: A Run Log That Is Safe to Share**

  > Every request a check makes to the lab is logged as one line: the check name, method, path, status, duration and identifiers. The full log goes to `target/logs/<module>.log`. `-DlogBodies=true` adds masked bodies. The access key, the password and patient names never appear. (FS §2.6)

  - [ ] Task: `api-client` main code.
    - `http/LogMasker`: a pure class. It replaces `name` and `password` values at any depth of a JSON body with `***`, and turns a non-JSON body into `<non-JSON body, N chars>`.
    - `http/RequestLogFilter`: a RestAssured `Filter` registered in `LabRequestSpecs.create`.
      - At INFO on logger `lab.http` it writes `METHOD path -> status in N ms [patient=… order=… sample=…]`. Identifiers come from the path and from the response JSON fields `id`/`patientId`/`orderId`/`sampleId`, never from `name`.
      - It logs no headers and no query string.
      - When `logBodies` is true (`-DlogBodies`, then `LOG_BODIES`), it writes the masked request and response bodies at DEBUG.
    - Unit tests against canned requests and responses. RED-validate.
    - Add `slf4j-api` (compile) to `api-client`.
    - **[Agent: general-purpose]**
  - [ ] Task: Put the check name into every log line.
    - Write `common/.../junit/CheckNameInterceptor`, an `InvocationInterceptor`. Around the test-method, test-template-method, `beforeEach` and `afterEach` invocations it puts `check=<Class> › <display name>` into the MDC, and clears it afterwards.
    - Add `junit-jupiter-api` at provided scope if not already present.
    - Test with `junit-platform-testkit` that the MDC is set on the thread that actually runs the body under `junit.jupiter.execution.timeout.thread.mode.default=SEPARATE_THREAD`.
    - RED-validate.
    - **[Agent: general-purpose]**
  - [ ] Task: Wire up the log in the test modules.
    - Register `CheckNameInterceptor` on `LabTest` and `UiTest`.
    - Extend both `logback-test.xml` files:
      - console at INFO, pattern `%d{HH:mm:ss.SSS} [%thread] %X{check} %msg%n`;
      - file at DEBUG to `target/logs/api-tests.log` / `target/logs/ui-tests.log`, with `append=false`.
    - Add `NoConsolePrintingGuardTest` (in `api-tests`). It scans `common`, `api-client`, `test-data`, `api-tests` and `ui-tests` sources for `System.out`, `System.err` and `printStackTrace`, and excludes `mock-lab-app`.
    - Add `RunLogSecrecyTest` (an `@ApiCheck`). It registers a recipe patient, places an order and registers a sample, then reads its module's log file. The file must contain the sample identifier and must contain neither the patient name nor the access key.
    - RED-validate both.
    - **[Agent: testing-expert]**
  - [ ] Verify:
    - Start the lab on 18080.
    - Run `./mvnw clean test -DbaseUrl=http://localhost:18080 -DlogBodies=true -fae`. Expect green.
    - Quote 3 representative lines from `api-tests/target/logs/api-tests.log`, each with a check name and identifiers, plus one masked body line.
    - Grep `target/logs`, `target/surefire-reports` and the saved console output for:
      - `mock-lab-local-token`
      - `I_Like_This_Lab`
      - every `TEST-patient-` name, collected from the lab with `curl GET /api/patients`
      - `Authorization`
    - All four greps must return 0 hits.
    - Make one deliberate failure in a check and find all of its lines by grepping its name. Restore the check exactly.
    - Stop the lab and delete scratch files.
    - **[Agent: testing-expert]**

- [ ] **Slice 6: Workflow Checks Run Side by Side**

  > Workflow checks run concurrently, by default as many as there are cores. `-DchecksAtOnce=N` caps them, and `-DchecksAtOnce=1` runs them one at a time. (FS §2.3, workflow checks)

  - [ ] Task: `common/.../junit/ChecksAtOnceStrategy`, a `ParallelExecutionConfigurationStrategy`.
    - It reads `checksAtOnce` from the `ConfigurationParameters`, falling back to system properties. Unset means `Runtime.availableProcessors()`.
    - It returns parallelism = core pool = max pool = N, with `saturate` returning true.
    - A bad value fails with `checksAtOnce must be a positive whole number, got '<v>'`.
    - Add `junit-platform-engine` at provided scope to `common`.
    - Unit tests plus RED validation.
    - **[Agent: general-purpose]**
  - [ ] Task: Enable parallel execution in `api-tests/src/test/resources/junit-platform.properties`.
    - Set `parallel.enabled=true`, `mode.default=concurrent`, `mode.classes.default=concurrent`, `config.strategy=custom` and `config.custom.class=com.labflow.common.junit.ChecksAtOnceStrategy`. Keep the random order.
    - Audit for shared state:
      - grep `happy/` and `rejection/` for list or size assertions that could see another check's data, and fix any;
      - keep any self-test that touches shared files `@Isolated`.
    - Extend `CheckIndependenceGuardTest` (api) to require the parallel keys.
    - **[Agent: testing-expert]**
  - [ ] Verify:
    - Start the lab on 18080.
    - Run `./mvnw -pl api-tests -am test -DbaseUrl=http://localhost:18080 -Dgroups=api` 3 times in a row on the same lab, all green.
    - Count the distinct `[thread]` names on check lines in `api-tests/target/logs/api-tests.log`: more than 1 with the default, at most 2 with `-DchecksAtOnce=2`, and exactly 1 with `-DchecksAtOnce=1`.
    - Run `-DchecksAtOnce=abc`: it fails with the exact message.
    - Quote everything.
    - Stop the lab.
    - **[Agent: testing-expert]**

- [ ] **Slice 7: Screen Checks Run Side by Side, Each in Its Own Browser**

  > Screen checks also run concurrently. Every worker thread has its own Playwright and browser, and every check has its own window. Failure pictures and recordings stay separate per browser and per check. (FS §2.3, screen checks)

  - [ ] Task: Change `BrowserExtension` to one Playwright per worker thread.
    - Replace the single shared `Playwright` + `Browser` with one `BrowserHandle` (Playwright + Browser of the chosen type) per worker thread. It is held in a `ThreadLocal` and created lazily.
    - Register every handle in one root-store `CloseableResource` (a synchronized list), so all handles close once, after the last check.
    - Keep the fresh `BrowserContext` + `Page` per check, the tracing and the artifact rules.
    - Failure artifacts move to `target/ui-failures/<browser>/<TestClass>/<scenario>/`.
    - Update `ArtifactNamesTest`, `BrowserExtensionArtifactsTest` and the README path.
    - Mark `RecordingPauseTest` `@Isolated` (it has its own static Playwright).
    - **[Agent: testing-expert]**
  - [ ] Task: Enable the same parallel keys in `ui-tests/src/test/resources/junit-platform.properties`.
    - Keep the random order and the 2-minute `SEPARATE_THREAD` timeout.
    - Update the comment there, which today warns about the single shared Playwright.
    - Extend the ui `CheckIndependenceGuardTest` to require the parallel keys.
    - If screen checks turn out flaky in repeated runs, apply the documented fallback (`mode.default=same_thread`, classes still concurrent) and record why.
    - **[Agent: testing-expert]**
  - [ ] Verify:
    - Start the lab on 18080.
    - Run `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080 -Dgroups=ui` 3 times in a row on the same lab, all green.
    - Distinct `[thread]` names on check lines in `ui-tests/target/logs/ui-tests.log`: more than 1 by default, at most 2 with `-DchecksAtOnce=2`.
    - While a default run is in progress, `ps` shows more than one browser process.
    - Make one deliberate failure. Exactly one artifact folder appears under `target/ui-failures/chromium/…`. Restore the check exactly.
    - Delete the artifacts and stop the lab.
    - **[Agent: testing-expert]**

- [ ] **Slice 8: Documented Commands, Measured Times and Architecture Update**

  > The README explains every new command and option and records the measured one-at-a-time and side-by-side times. `architecture.md` matches what was built. (FS §2.1–§2.6 README criteria)

  - [ ] Task: Measure on one running lab (18080) and record the machine's core count.
    - Do one warm-up run.
    - Take the median `Total time:` of 3 runs each of:
      - `./mvnw test -Dgroups=regression` (default)
      - the same with `-DchecksAtOnce=1`
      - `./mvnw test -Dgroups="smoke | !(api | ui)"`
    - Confirm that side by side is faster than one at a time, and that smoke takes under a third of regression.
    - Hand the numbers to the README task.
    - **[Agent: testing-expert]**
  - [ ] Task: Update `README.md` → `## Running Tests`.
    - A labels table with the four subset commands, quoted.
    - The "0 checks ran" note.
    - Settings table rows for `ENVIRONMENT`/`-Denvironment`, `BROWSER`/`-Dbrowser` (with the Codespaces `BROWSER` note), `-DchecksAtOnce` (with the advice to lower it on small machines) and `-DlogBodies`.
    - The WebKit install command, and a note that Firefox is not supported and why.
    - A "Run log" paragraph: where the files are, what is and is not logged, and how to find a failing check's lines.
    - The measured times table.
    - Keep the existing sections intact.
    - **[Agent: testing-expert]**
  - [ ] Task: Update `context/product/architecture.md` (via the `/awos:architecture` conventions) and mark the roadmap items done:
    - the `test-data` module;
    - `common`'s provided JUnit dependencies and `slf4j-api`;
    - labels by base class;
    - `ChecksAtOnceStrategy`;
    - per-thread browsers;
    - `BROWSER`/`ENVIRONMENT`;
    - the run log.
    - Fix the `<description>` in `common/pom.xml`, which still says it has no third-party runtime dependencies (it now has `slf4j-api`), and the matching line in `technical-considerations.md` §2.1.
    - Tick Phase 2 "Reliable, Isolated Test Execution" and "Configuration & Logging" in `roadmap.md`.
    - **[Agent: general-purpose]**
  - [ ] Verify:
    - Follow the README literally on a fresh `./mvnw clean`: run each documented command once against the lab on 18080. Every one behaves as described.
    - Confirm the times in the README match the measurement output.
    - Stop the lab and delete scratch files.
    - **[Agent: testing-expert]**

- [ ] **Slice 9: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.
  - [ ] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 005-reliable-isolated-execution` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [ ] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**
