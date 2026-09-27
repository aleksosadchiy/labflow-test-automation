# Tasks: Screen Checks with One Description per Screen (Phase 2)

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

After each slice, the project still builds and runs with `./mvnw` from the repository root, one more piece of the functional spec works end to end, the existing `common`/`api-client`/`api-tests` checks stay green, and nothing under `mock-lab-app/` changes.

**Verification conventions (used by every Verify task):**
- **Lab on a spare port.** The framework needs a running lab but never starts one. Verify tasks start their own on a spare port so they never disturb a lab the engineer may be running on 8080:
  1. Build the lab jar if it is missing: `cd mock-lab-app && ./mvnw -q -B clean package -DskipTests`.
  2. Start it: `java -jar mock-lab-app/target/mock-lab-app.jar --server.port=18080 &`.
  3. Wait for it: `curl -sf --retry 30 --retry-delay 1 --retry-connrefused http://localhost:18080/health`.
  4. Point the checks at it with `-DbaseUrl=http://localhost:18080`.
  5. At the end, stop every process you started and delete temp files. Never touch anything listening on 8080.
- **Maven.** Run it from the repo root through the root wrapper (`./mvnw`; `mvn` is not on the PATH). Never use `-q` when checking the "Tests run" summary, and never run `install`. The local JDK is 26 while the code targets Java 17. Maven prints harmless `WARNING:` lines about this; ignore them.
- **Browser.** Chromium for Playwright 1.63.0 (`chromium-1243`) is already present in `~/Library/Caches/ms-playwright` on this machine. Screen checks run headless unless a task says otherwise.
- **Agent split** (same rule as spec 003). `testing-expert` writes only test code: the checks, the test-side support and guards, every Verify task, and the final QA slice. Tasks that create code under `ui-tests/src/main` (Page Objects, `UiCredentials`, `RecordingPause`) go to `general-purpose`, which writes that code together with its unit tests and proves them with RED validation. Maven wiring goes to `github-actions-cicd`.
- **Evidence.** Every task that adds tests proves them with RED validation: break the behaviour the test covers, watch the test fail, restore the code exactly, watch it pass. Every Verify task quotes real command output.
- **Artifacts.** Verify tasks delete any screenshots, traces and `ui-tests/target/ui-failures/` contents they produced. The Feature Testing & Regression slice keeps its tests.

---

- [x] **Slice 1: Sign In Through the Real Screen with One Command**

  > The `ui-tests` module exists, the browser is installed with one documented command, and the first screen check signs in with the lab's valid details and lands on the Sample List. It runs headless by default and visibly with `-Dheaded=true`. (FS §2.1 ¶1–4 and ¶6, §2.2 ¶4, §2.4 ¶1)

  - [x] Task: Build wiring.
    - Root `pom.xml`:
      - Add the property `playwright.version=1.63.0` and a `com.microsoft.playwright:playwright` entry in `dependencyManagement`.
      - Add the property `exec-maven-plugin.version=3.6.4`.
      - Add `ui-tests` to `<modules>` after `api-client`.
      - In the root `<build><plugins>`, declare `exec-maven-plugin` with `<inherited>false</inherited>`, a plugin-level dependency on `playwright` at `${playwright.version}`, `mainClass=com.microsoft.playwright.CLI`, `includeProjectDependencies=false`, `includePluginDependencies=true`, and default arguments `install chromium`. This makes `./mvnw -N exec:java` the one-time install command. `-Dexec.args="show-trace <zip>"` reuses the same plugin.
    - Create `ui-tests/pom.xml` with no versions:
      - compile scope: `common`, `api-client`, `playwright`;
      - test scope: `junit-jupiter`, `junit-platform-launcher`, `assertj-core`.
    - Leave Surefire at its defaults. Do not touch `mock-lab-app/`.
    - **[Agent: github-actions-cicd]**
  - [x] Task: Credentials and the first two Page Objects, in `ui-tests/src/main/java/com/labflow/ui/`.
    - `config/Password`: blank values are rejected, `toString()` returns `***`, and the raw value is available only through `reveal()`.
    - `config/UiCredentials`:
      - Record `(username, Password)`, loaded with `load(sysProps, env)` and `fromEnvironment()`.
      - Username comes from the env var `MOCK_LAB_USERNAME`, default `LabTech`. Password comes from the env var `MOCK_LAB_PASSWORD` **only** (a system property is ignored), default `I_Like_This_Lab`. Blank values count as unset.
      - `toString()` masks the password.
    - `pages/LoginPage`: `open(page, config)`, `signIn(UiCredentials)` → `SampleListPage`, `isShown()`.
      - Locators: `#username` and `#password`; the button by `getByRole(BUTTON, "Sign In")`.
      - `signIn` returns only once the Sample List screen is ready.
    - `pages/SampleListPage`: `isShown()`, which waits on the "Samples" heading.
    - Page Objects hold no assertions and no JUnit.
    - Unit tests `UiCredentialsTest` and `PasswordTest` cover precedence, blank values, the ignored system property, defaults, and masking.
    - **[Agent: general-purpose]**
  - [x] Task: Test-side support, in `ui-tests/src/test/java/com/labflow/ui/tests/`.
    - `support/BrowserExtension`:
      - One `Playwright` + Chromium `Browser` per run, lazily created in the root `ExtensionContext.Store` as a `CloseableResource`.
      - Before each test: a fresh `BrowserContext` (viewport 1280×800) and `Page`, injected into the test instance. Close the context after each test.
      - `headless = !Boolean.getBoolean("headed")`; `slowMo` comes from `-DslowMo`, default 0.
      - If the browser binaries are missing, rethrow the launch failure with an added line naming `./mvnw -N exec:java`.
    - `support/UiTest`:
      - Base class carrying `@ExtendWith({ScenarioNameExtension.class, BrowserExtension.class})`.
      - Fields: `protected final LabConfig config`, `protected final UiCredentials credentials`, `protected final LabClient lab`, and `protected Page page`.
      - Helper: `signedIn()` → `SampleListPage`.
    - `support/ScenarioNameExtension` and `support/UniqueData`: copy them from `api-tests`, with a comment saying why they are copied and not moved (see tech spec §2.3).
    - `src/test/resources/junit-platform.properties`: random method and class order.
    - `happy/SignInTest#validSignInOpensSampleList`:
      - `@DisplayName("Signing in with the lab's valid details opens the Sample List screen")`.
      - Header comments `// @layer: e2e`, `// @spec: 004-ui-automation-page-objects`, `// @regression`.
      - The test contains no locators or URLs.
    - **[Agent: testing-expert]**
  - [x] Verify: Start the lab on 18080, then do all of the following:
    - Run `./mvnw -N exec:java`. It exits 0 and reports chromium as already installed or installs it.
    - Run `./mvnw clean test -DbaseUrl=http://localhost:18080`. All modules including `ui-tests` are green; quote the per-module counts.
    - Run `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080`. The reactor is exactly `common`, `api-client`, `ui-tests` (no `api-tests`, no `mock-lab-app`) and the result is green.
    - Run the same command again with `-Dheaded=true -DslowMo=200`. It is green and a Chromium process is visible during the run: capture `ps` output that shows no `--headless` flag, then delete that capture.
    - Missing browser: run with `PLAYWRIGHT_BROWSERS_PATH` pointed at an empty temp directory and `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`. The failure message names `./mvnw -N exec:java`.
    - Check that `MOCK_LAB_PASSWORD`'s value does not appear in the console output or in `ui-tests/target/surefire-reports`.
    - Check that `git status` shows no change under `mock-lab-app/`.
    - Stop the lab and delete temp directories and output.
    - **[Agent: testing-expert]**

- [x] **Slice 2: Sign-In Refusals, and Stop Early When the Lab Is Down**

  > Wrong or blank details are refused with the lab's exact messages, screens require signing in, and a run against a stopped lab stops once with a clear message. (FS §2.1 ¶5, §2.3, §2.4 ¶2–4)

  - [x] Task: Extend `LoginPage` with:
    - `signInExpectingError(username, password)`, which returns `LoginPage` once `#error-message` is visible;
    - `errorMessage()`.
    
    Extend `SampleListPage` with `open(page, config)`. It navigates, and the caller then uses `LoginPage.isShown()` when a redirect is expected.

    Only Page Objects know the URLs. Update the unit tests where relevant.
    **[Agent: general-purpose]**
  - [x] Task: Add `support/UiPreflightListener` (a `LauncherSessionListener` calling `LabPreflight.check(LabConfig.fromEnvironment())`), registered in `ui-tests/src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener`.

    Add `rejection/SignInRejectionTest` with three checks:
    - `wrongPasswordIsRefused`: a deliberately fake password; expect the message `"Invalid username or password."` from `LabMessages`, or the same literal if it is not in `LabMessages`, and the Sign In screen stays.
    - `blankCredentialsAreRefused`: `@ParameterizedTest` with a named blank-username row and a named blank-password row; expect `"Username and password are required."`.
    - `screensRequireSignIn`: open the Sample List signed out, and the Sign In screen is shown.

    All three get business `@DisplayName`s. **[Agent: testing-expert]**
  - [x] Task: _(Added after the first Slice 2 Verify found that business names never reach the run output, FS §2.3 ¶3.)_ In the root `pom.xml` `pluginManagement`, configure `maven-surefire-plugin` with `statelessTestsetReporter` = `JUnit5Xml30StatelessReporter` (phrased test-case method names) and `statelessTestsetInfoReporter` = `JUnit5StatelessTestsetInfoReporter` (phrased class names in the Running and summary lines). Report file names and the XML `classname` stay the Java class name, so tools can still link results back to source. Both `api-tests` and `ui-tests` then list every check, including each parameterized row, under its `@DisplayName` in `TEST-*.xml`. Surefire's console never lists passing tests one by one; it shows a check's name only when that check fails. Add a short comment saying why. Leave every other Surefire setting at its default. Make sure `./mvnw clean test` stays green and `api-tests`' guard tests (which read reports or sources) still pass. **[Agent: github-actions-cicd]**
  - [x] Verify: Start the lab on 18080.
    - `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080` is green, and `TEST-*SignInRejectionTest.xml` lists every check, including each parameterized row, under its business name.
    - Stop the lab and run the same command. It aborts once with a `LAB PRE-FLIGHT FAILED:` line naming `http://localhost:18080`, and no screen check reports its own failure.
    - Delete temp output.
    
    **[Agent: testing-expert]**

- [x] **Slice 3: Register a Sample on Screen and See It Listed as Accessioned**

  > A check prepares its own unique patient through the lab's connected interface, registers a sample on the Create Sample screen, and finds it in the Sample List by its id with status Accessioned. Every Create Sample refusal message is covered. (FS §2.5, §2.8 ¶1)

  - [x] Task: Page Objects.
    - `CreateSamplePage`:
      - `open(page, config)`;
      - `createSample(patientId, sampleTypeText)`, which returns `SampleDetailsPage` because the lab redirects to the details screen;
      - `createSampleExpectingError(...)` → `CreateSamplePage`;
      - `errorMessage()`.
      - Locators: `#patientId`, `#sampleType`, and the button by `getByRole(BUTTON, "Create Sample")`.
    - `SampleListPage`:
      - `startNewSample()` → `CreateSamplePage`;
      - `rowFor(sampleId)` → `Optional<SampleRow>`. It matches the row whose first cell equals the id, never by position, and maps the raw status text with `WorkflowStage.valueOf`.
    - `SampleRow` record `(sampleId, patientName, WorkflowStage status)`.
    - `SampleDetailsPage` (minimal for now): `open(page, config, id)`, `sampleId()`, `backToList()` if the list must be reached by navigation.

    Add unit tests where there is pure logic, for example the row/status mapping. **[Agent: general-purpose]**
  - [x] Task: Add the `UiTest.newPatient()` helper, which calls `lab.patients().register(UniqueData.name("ui-patient"))`.

    Add `happy/SampleRegistrationTest#registeredSampleIsListedAsAccessioned`, which checks the patient name and `WorkflowStage.ACCESSIONED` in the row found by the new sample's id.

    Add `rejection/SampleRegistrationRejectionTest` with:
    - `missingFieldIsRequired`: `@ParameterizedTest` for blank patient → `"Patient is required."` and blank type → `"Sample type is required."`;
    - `unknownPatientIsRefused` → `"Patient not found."`;
    - `unsupportedSampleTypeIsRefused` → `"Invalid sample type."`, using a known patient.

    Expected texts come from `LabMessages`. **[Agent: testing-expert]**
  - [x] Verify: With the lab on 18080:
    - `./mvnw -pl ui-tests -am test -DbaseUrl=http://localhost:18080` is green.
    - Run it a second time against the same lab without a restart; it is green again.
    - Run `SampleRegistrationTest` alone with `-Dtest=…`; it is green.
    - Delete temp output.
    
    **[Agent: testing-expert]**

- [x] **Slice 4: Follow a Sample Through the Workflow on the Details Screen**

  > The Details screen shows all eight stages with the current one marked, Advance moves the sample on without sleeps, the final stage offers the report instead of Advance, and an unknown id says "Sample not found." (FS §2.6, §2.8 ¶2)

  - [x] Task: Complete `SampleDetailsPage`:
    - `patientName()`;
    - `currentStatus()` → `WorkflowStage`;
    - `stages()` → `List<StageView>`, where `StageView` is a record `(WorkflowStage, StageState)` and `StageState` is an enum `COMPLETED`/`CURRENT`/`REMAINING` taken from the `stage-*` CSS class;
    - `advance()`, which clicks `#advance-button` and then waits with `page.waitForCondition` until `#current-status` shows the next stage, then returns `SampleDetailsPage`;
    - `canAdvance()` and `canViewReport()`, both checked only after the screen-ready wait;
    - `openReport()` → `ReportPage` (a stub is fine until Slice 5);
    - `notFoundMessage()`.

    No `Thread.sleep` or `waitForTimeout`. Unit-test `StageState` parsing. **[Agent: general-purpose]**
  - [x] Task: Add `happy/SampleWorkflowTest` with:
    - `newSampleStartsAtAccessioned`: eight stages in workflow order, `ACCESSIONED` current, the rest remaining;
    - `advancingOnceReachesReadyForProcessing`: `ACCESSIONED` completed, `READY_FOR_PROCESSING` current, six remaining;
    - `finalStageOffersReportInsteadOfAdvance`: prepare the sample with `lab.samples().walkTo(id, RESULT_READY)`, advance once on screen, then check Advance is gone and View Report is shown.

    Add `rejection/SampleDetailsRejectionTest#unknownSampleIsNotFound`, which uses a `UniqueData`-based id that does not exist. **[Agent: testing-expert]**
  - [x] Verify: With the lab on 18080:
    - The `ui-tests` run is green three times in a row. Quote the counts and note that the class order differs between runs.
    - `grep -rnE "Thread\.sleep|waitForTimeout" ui-tests/src` finds nothing.
    - Delete temp output.
    
    **[Agent: testing-expert]**

- [x] **Slice 5: The Report, Including One Full Journey Through the Screens**

  > One check walks a sample from registration to Report Generated using only the screens and reads its report. Another sees "Report is not ready yet." for an unfinished sample. (FS §2.7)

  - [x] Task: Add `ReportPage` with:
    - `open(page, config, sampleId)`;
    - `sampleId()`, `testName()`, `resultValue()`, `generatedAt()` (as displayed text);
    - `notReadyMessage()`.

    Make `SampleDetailsPage.openReport()` real, using `#view-report-link`. **[Agent: general-purpose]**
  - [x] Task: Add `happy/ReportJourneyTest#fullJourneyThroughScreensEndsInReport`. It is the only screens-only journey: after the API patient, it registers the sample on screen, advances seven times on screen, opens the report, and checks that the report's sample id matches and that the test name, result value and generation date are non-blank.

    Add `rejection/ReportRejectionTest#reportNotReadyBeforeFinalStage`, which prepares a sample through the API at a mid stage (for example `PROCESSING`) and checks the `"Report is not ready yet."` text from `LabMessages`. **[Agent: testing-expert]**
  - [x] Verify: With the lab on 18080:
    - `./mvnw clean test -DbaseUrl=http://localhost:18080` is green across all modules; quote the counts.
    - Run `ReportJourneyTest` alone; it is green.
    - Delete temp output.
    
    **[Agent: testing-expert]**

- [x] **Slice 6: Failures Explain Themselves, Without Leaking the Password**

  > A failing screen check names its scenario, saves a screenshot and a Playwright trace in a folder named after the scenario, and keeps nothing for passing checks. The real password appears in no output, file name or trace. (FS §2.9)

  - [x] Task: Add `recording/RecordingPause` and a shared trace-options constant in `ui-tests/src/main`. The pause stops tracing with no path, which discards everything recorded so far, including the network log. It then runs the action and restarts tracing with the same options. When tracing is not running, it just runs the action.

    Use it in `LoginPage.signIn(UiCredentials)` around the password fill, the submit, and the wait for the Sample List, so recording resumes only after the login form is gone. `signInExpectingError` stays unpaused, because its values are deliberately fake.

    Tests:
    - unit tests for the pause;
    - `SignInTraceTest`, which signs in against the real lab with tracing on and asserts that the saved trace contains the password in no entry: not in actions, DOM snapshots or the network log.

    _(Reworked during Slice 6: a chunk-based pause around the fill alone still leaked the password through DOM snapshots and the network log. See tech spec §2.5.)_ **[Agent: general-purpose]**
  - [x] Task: Extend `BrowserExtension`.
    - Start tracing per test with the shared trace options from `ui-tests/src/main` (`screenshots=true`, `snapshots=true`, `sources=false`).
    - In one `afterEach`, do the following in order:
      - If `getExecutionException()` is present, save `screenshot.png` and stop tracing to `trace.zip` in `ui-tests/target/ui-failures/<TestClass>/<sanitised-display-name>[-<index>]/`.
      - Otherwise stop tracing with no path.
      - Then close the context.
    - The folder-name sanitiser lower-cases, turns non-alphanumeric runs into `-`, and truncates. It is built only from the class and display names.
    - Unit-test the sanitiser. One of the cases is a display name next to a password value, and the password must never appear in the result.
    
    **[Agent: testing-expert]**
  - [x] Verify: Start the lab on 18080.
    - Temporarily change one expected value in `SampleRegistrationTest` so it fails, run `ui-tests`, and check:
      - the console shows `[Scenario: …]` with expected and actual values;
      - exactly one folder under `ui-tests/target/ui-failures/` holds `screenshot.png` and `trace.zip`;
      - `./mvnw -N exec:java -Dexec.args="show-trace --help"` works, and the trace opens in `show-trace` without errors (headless check: the command starts, then stop it);
      - `unzip -p trace.zip | grep -c "$MOCK_LAB_PASSWORD value"` (literally the default `I_Like_This_Lab`) returns 0. If it does not, report it as a failure; `SignInTraceTest` should already rule this out.
    - Restore the test exactly. A green run leaves `ui-failures/` absent or empty.
    - Delete all artifacts and temp output.
    
    **[Agent: testing-expert]**

- [x] **Slice 7: The Structure Is Enforced and Documented**

  > Guard tests keep locators and URLs out of checks, assertions out of Page Objects and sleeps out of everything. The README explains how to install the browser, run and watch the screen checks, and investigate a failure. (FS §2.1 ¶3, §2.2 ¶1–3 and ¶5, §2.3)

  - [x] Task: Add plain-text guard tests in `ui-tests/src/test/java/.../guard/`, in the same style as `api-tests` (`CheckSources`-like helper, forbidden strings split so the guard does not match itself, fail loudly if no files are found):
    1. `happy`/`rejection` contain no `locator(`, `getByRole(`, `"#`, `.navigate(`, `"/samples`, `"/login`, or `com.microsoft.playwright.options`.
    2. `ui/pages/**` contains no `assertThat`, `Assertions` or `PlaywrightAssertions`.
    3. Nothing in `ui-tests/src` uses `Thread.sleep` or `waitForTimeout`.
    4. Wiring:
       - `junit-platform.properties` sets random order;
       - the checks avoid `BeforeAll`, `AfterAll`, `TestInstance` and `jupiter.api.Order`;
       - every check extends `UiTest`;
       - `UiPreflightListener` is registered through ServiceLoader.
    
    Prove each guard with RED validation. **[Agent: testing-expert]**
  - [x] Task: Add a "Screen checks" part under `## Running Tests` in `README.md`, in the existing bold-label style:
    - the one-time `./mvnw -N exec:java` (with `--with-deps` noted for Linux);
    - `./mvnw -pl ui-tests -am test`, and that `./mvnw clean test` now includes it (`-fae` for both verdicts);
    - `-Dheaded=true` and `-DslowMo`;
    - `MOCK_LAB_USERNAME` and `MOCK_LAB_PASSWORD` added to the Settings table, with the password env-only;
    - where failure artifacts land;
    - how to open a trace (`./mvnw -N exec:java -Dexec.args="show-trace <zip>"` or trace.playwright.dev).
    
    Update `## Planned Structure` to list `ui-tests`. **[Agent: testing-expert]**
  - [x] Verify: With the lab on 18080:
    - Full `./mvnw clean test` is green.
    - One-element change: locally rename one id in a lab template (for example `#current-status`), restart the lab, and show that the failing checks are fixed by editing exactly one Page Object. Then revert both the lab and the Page Object exactly, and `git diff --stat` is empty for them.
    - Follow the README's screen-check steps verbatim, and every command works.
    - Delete temp output.
    
    **[Agent: testing-expert]**

- [ ] **Slice 8: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.
  - [ ] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 004-ui-automation-page-objects` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [ ] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**
