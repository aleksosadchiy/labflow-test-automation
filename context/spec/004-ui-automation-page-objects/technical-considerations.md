# Technical Specification: Screen Checks with One Description per Screen (Phase 2)

- **Functional Specification:** [functional-spec.md](./functional-spec.md)
- **Status:** Draft
- **Author(s):** Alex, with the `testing-expert` and `github-actions-cicd` specialists

---

## 1. High-Level Technical Approach

A new Maven module, `ui-tests`, joins the reactor after `api-client`. It follows the same split the API side uses, where `api-client` talks to the lab and `api-tests` holds the checks, but inside one module:

- **`src/main`** holds the **Page Objects**: one class per lab screen, plus small value records. They use only Playwright for Java and `common`'s domain types. They have no JUnit dependency and make no assertions.
- **`src/test`** holds the **checks** (`happy/`, `rejection/`), a `UiTest` base class, a JUnit extension that owns the browser lifecycle and failure artifacts, a pre-flight listener, and guard tests.

Each check does three things:

1. It prepares its data (a patient, and sometimes a sample partway through the workflow) through the existing `api-client`. The lab has no patient screen, and API setup is fast and stable. The one full end-to-end journey (FS 2.7) is the exception and uses screens throughout.
2. It drives Chromium through the Page Objects.
3. It asserts with AssertJ on the values the Page Objects return.

The lab itself is untouched. Nothing in the system under test changes.

Browser: Chromium only, headless by default, with `-Dheaded=true` to show it. Artifacts: a screenshot and a Playwright trace, kept only for failed checks, under `ui-tests/target/ui-failures/`.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1. Build wiring (Maven)

| Change | Where | Detail |
| --- | --- | --- |
| New module | root `pom.xml` `<modules>` | `ui-tests`, listed after `api-client` |
| Version pin | root `<properties>` + `<dependencyManagement>` | `playwright.version=1.63.0` (latest on Maven Central, 2026-09-14), artifact `com.microsoft.playwright:playwright` |
| Browser install helper | root `<build><plugins>` | `exec-maven-plugin` 3.6.4 (pinned via property). Settings: `<inherited>false</inherited>`, a plugin-level dependency on `playwright`, `mainClass=com.microsoft.playwright.CLI`, `includeProjectDependencies=false`, `includePluginDependencies=true`, default args `install chromium` |
| Module POM | `ui-tests/pom.xml` | compile: `common`, `api-client`, `playwright` (Page Objects in `src/main` need it). test: `junit-jupiter`, `junit-platform-launcher` (pre-flight listener), `assertj-core`. No versions (all managed at the root). Surefire is inherited from the root. |
| Readable test names | root `pluginManagement` for Surefire | JUnit 5 stateless-testset and console reporters with phrased names, so `@DisplayName` and parameterized row names appear in the console and in the XML/TXT reports for every module (FS §2.3 ¶3). Every other Surefire setting keeps its default. _Added during Slice 2 verification._ |

**Commands** (all from the repository root, with the lab running):

| Purpose | Command | Notes |
| --- | --- | --- |
| One-time browser install | `./mvnw -N exec:java` | `-N` builds the root POM only. The root has no project dependencies, so this needs no reactor build or `~/.m2` install. For Linux CI later: `-Dexec.args="install --with-deps chromium"` |
| Screen checks only | `./mvnw -pl ui-tests -am test` | Reactor = `common`, `api-client`, `ui-tests`. Their quick self-checks run too, which matches FS 2.1. `api-tests` is not included. |
| Everything | `./mvnw clean test` | Now includes `ui-tests`. Mention `-fae` in the README to get both API and UI verdicts in one run. |
| Show the browser | append `-Dheaded=true` (optionally `-DslowMo=250`) | Surefire forwards Maven user properties to the forked JVM. These are not secrets, so they may appear in Surefire XML. |
| Open a saved trace | `./mvnw -N exec:java -Dexec.args="show-trace <path-to-trace.zip>"` | Or drag the file onto `trace.playwright.dev`, which runs locally in the browser. No Node toolchain is needed either way. |

_Why not a manual `exec:java` inside `ui-tests`: with `-pl ui-tests` it looks for unreleased sibling SNAPSHOTs in `~/.m2`. Why not rely on auto-download: `Playwright.create()` silently downloads **every** browser (about 1 GB) inside the first test's timeout._

**Architecture doc correction needed:** `architecture.md` §4 says `mvn playwright:install`, and no such plugin exists for Java. Replace it with the command above, and record the `ui-tests` module and the `headed` / `MOCK_LAB_*` settings, via `/awos:architecture`.

### 2.2. Configuration

| Setting | Source | Default | Notes |
| --- | --- | --- | --- |
| Lab address | existing `LabConfig` (`-DbaseUrl` > `BASE_URL` > `http://localhost:8080`) | unchanged | Screens live at the root, not `/api` |
| Sign-in username | env `MOCK_LAB_USERNAME` | `LabTech` | Same variable name the lab reads, the same precedent as `API_TOKEN` |
| Sign-in password | env `MOCK_LAB_PASSWORD` **only**, never `-D` | `I_Like_This_Lab` (the lab's documented local default, not a secret) | A `-D` value would be written into Surefire XML |
| Headed mode | `-Dheaded` (`Boolean.getBoolean`) | `false` | Must never default to true (CI has no display) |
| Slow motion | `-DslowMo` (ms) | `0` | Debug aid only |

`UiCredentials` (`ui-tests/src/main/.../config/`) loads the username and password with the same testable `load(sysProps, env)` / `fromEnvironment()` shape as `LabConfig`. The password is wrapped in a small `Password` type whose `toString()` is `***`. `UiCredentials` stays out of `common`, so the API modules don't carry a UI-only concern. Browser options (`headed`, `slowMo`) are read in the test-side extension.

### 2.3. Component breakdown

**`ui-tests/src/main/java/com/labflow/ui/`**

| Class | Responsibility |
| --- | --- |
| `pages/LoginPage` | `open(page, config)`. `signIn(UiCredentials)` → `SampleListPage`. `signInExpectingError(user, pass)` → `LoginPage`. `errorMessage()`, `isShown()` |
| `pages/SampleListPage` | `rowFor(sampleId)` → `Optional<SampleRow>`, located by the unique id and never by position (the list is shared). `emptyStateMessage()`, `startNewSample()` → `CreateSamplePage` |
| `pages/CreateSamplePage` | `open(page, config)`. `createSample(patientId, typeText)` → `SampleDetailsPage` (the lab redirects to the details screen). `createSampleExpectingError(...)` → `CreateSamplePage`. `errorMessage()`, `backToList()` if needed |
| `pages/SampleDetailsPage` | `open(page, config, sampleId)`. `sampleId()`, `patientName()`, `currentStatus()` → `WorkflowStage`, `stages()` → `List<StageView>`. `advance()` → `SampleDetailsPage`. `canAdvance()`, `canViewReport()`, `openReport()` → `ReportPage`, `notFoundMessage()` |
| `pages/ReportPage` | `open(page, config, sampleId)`. `sampleId()`, `testName()`, `resultValue()`, `generatedAt()`, `notReadyMessage()` |
| `pages/SampleRow` | record `(sampleId, patientName, WorkflowStage status)` |
| `pages/StageView`, `pages/StageState` | record `(WorkflowStage stage, StageState state)`; enum `COMPLETED / CURRENT / REMAINING`, taken from the `stage-*` CSS class |
| `config/UiCredentials`, `config/Password` | see §2.2 |
| `recording/RecordingPause` | Pauses the trace around the real-password entry (see §2.5) |

**`ui-tests/src/test/java/com/labflow/ui/tests/`**

| Class | Responsibility |
| --- | --- |
| `support/UiTest` | Abstract base, mirroring `LabTest`. It carries `@ExtendWith({ScenarioNameExtension, BrowserExtension})` and exposes `protected final LabClient lab`, `protected Page page` (set by the extension), `config` and `credentials`. It also has small business helpers such as `signedIn()` → `SampleListPage` and `newPatient()` (via `lab.patients().register(UniqueData.name("ui-patient"))`) |
| `support/BrowserExtension` | Owns the browser lifecycle and failure artifacts (§2.4, §2.5) |
| `support/ScenarioNameExtension`, `support/UniqueData` | **Copied** from `api-tests` (about 30 lines). Moving them into `common/src/main` would force JUnit onto production code. The planned `test-data` module (next Phase 2 spec) is where shared test helpers get consolidated. |
| `support/UiPreflightListener` | A `LauncherSessionListener` that calls the existing `LabPreflight.check(LabConfig.fromEnvironment())`. Registered in `ui-tests/src/test/resources/META-INF/services/…`, because ServiceLoader registration is per module classpath |
| `happy/*`, `rejection/*` | Checks (§2.7) |
| `guard/*` | Guard tests (§4) |

**Page Object rules** (what goes in, what stays out):
- Every navigation method returns the Page Object of the screen the user lands on. Outcomes are split by name (`signIn` vs `signInExpectingError`), so the flow between screens is visible in the check (FS 2.2).
- Only Page Objects know URLs. Checks reach a screen directly through static factories such as `SampleDetailsPage.open(page, config, id)`, and never see a path.
- Page Objects return Strings, enums, records and booleans. They contain no assertions.
- Raw status text is mapped with `WorkflowStage.valueOf(text)`. The lab's screens show the enum constant name (`ACCESSIONED`), not the display name. That is how the lab behaves today, not a bug to fix here (FS: "screens tested as they are"). Checks compare `WorkflowStage` values, so "Accessioned" in the FS means `WorkflowStage.ACCESSIONED`.
- **Locators:** use the lab's ids wherever they exist (`#username`, `#error-message`, `#current-status`, `#advance-button`, `#report-*`). For the two unnamed buttons ("Sign In", "Create Sample"), use `getByRole(BUTTON, name)`. Playwright recommends this when no id exists, and it also pins the visible label.

### 2.4. Browser lifecycle (`BrowserExtension`)

| Object | Lifetime | Mechanism |
| --- | --- | --- |
| `Playwright` + `Browser` (Chromium) | Once per run | Lazily created in the **root** `ExtensionContext.Store` as a `CloseableResource`. JUnit closes it when the run ends. No `@BeforeAll` or statics in checks. |
| `BrowserContext` + `Page` | Once per test method | Created in `beforeEach` with a fixed viewport (1280×800) and assigned to the test instance's `page`. Tracing starts here. Closed in `afterEach` after artifact capture. |

This is ready for parallel execution later. Every test has its own context and page, `Browser.newContext()` is safe to call concurrently, and `Playwright`/`Browser` are shared only through the store. Creating `Playwright` once also avoids re-extracting the Node driver into `java.io.tmpdir` for every test.

If `chromium.launch()` fails because the browser binaries are missing, the extension rethrows the error with one added line naming `./mvnw -N exec:java`. That failure is already clear on the first test, so it needs no separate session listener. The lab-unreachable case does, because it would otherwise fail every check the same way, and that is what `UiPreflightListener` covers.

### 2.5. Failure artifacts and secrecy

In one `afterEach` step, capture first and then close, so there is no ordering question between two extensions:

1. If `context.getExecutionException()` is present:
   - `page.screenshot(…/screenshot.png)`
   - `tracing.stop(path …/trace.zip)`
2. Otherwise, `tracing.stop()` with no path, so the trace is discarded and nothing is kept.
3. `browserContext.close()` runs in both cases.

**Location:** `ui-tests/target/ui-failures/<TestClass>/<sanitised-display-name>[-<invocation index>]/`. Folder names come only from class and display names (lower-case, non-alphanumeric runs turned into `-`, truncated), never from input values, so the password can never appear in a path. The folder is under `target/`, so `clean` wipes it and it is already git-ignored. CI can later upload `**/target/ui-failures/**`.

**Tracing options:** `screenshots=true`, `snapshots=true`, `sources=false`.

**Keeping the real password out of the recording (FS 2.9):** Playwright records `fill()` arguments in the trace's action log and has no built-in masking. The control is therefore structural:
- The real password is typed only by `LoginPage.signIn(UiCredentials)`.
- Around that single `fill`, `RecordingPause` calls `tracing.stopChunk()` with no path (discarding what was recorded so far: opening the sign-in screen and typing the username) and `tracing.startChunk()` right after. Recording resumes at the submit click.
- The deliberately wrong or blank credentials in the rejection checks are not secrets, so they are recorded normally, which is useful when debugging.
- **Assumption, to verify once built:** force a sign-in-dependent check to fail, open its `trace.zip`, and confirm that:
  - the password appears in no action,
  - the DOM snapshots do not expose the `type=password` field's value,
  - `stop(path)` exports the post-resume chunk.
  If snapshots do expose the value, clear the field before resuming the chunk, or move the resume point to after the redirect.

**Scenario-named failures:** the copied `ScenarioNameExtension` prefixes assertion failures with `[Scenario: <display name>]`. AssertJ's `as("…")` descriptions state the expected screen value next to what was read (FS 2.9 ¶1).

### 2.6. Waiting strategy (anti-flakiness)

There is one rule: **Page Objects own synchronisation, and checks only compare resolved values with AssertJ.**
- Every navigation method returns only when the destination screen is ready. It waits for the destination's identifying element, for example the `<h1>` text or `#sample-details`, through Playwright's auto-waiting `Locator.waitFor()`.
- `advance()` posts and redirects back to the same URL. After the click it waits with `page.waitForCondition(…)` until `#current-status` shows the next stage, which is Playwright-managed polling with its default timeout.
- Absence checks (`canAdvance()` when there is no button) run only after the screen-ready wait, so `isVisible()` (no waiting) is safe.
- `PlaywrightAssertions` is not used. This keeps one assertion style (AssertJ) across the API and UI checks, consistent with spec 003.
- `Thread.sleep` / `waitForTimeout` are banned by a guard test.

### 2.7. Check inventory (FS 2.4–2.7)

| Class | Method (→ `@DisplayName` business sentence) | FS |
| --- | --- | --- |
| `happy/SignInTest` | `validSignInOpensSampleList` | 2.4¶1 |
| `rejection/SignInRejectionTest` | `wrongPasswordIsRefused` | 2.4¶2 |
|  | `blankCredentialsAreRefused` (`@ParameterizedTest`: blank username / blank password) | 2.4¶3 |
|  | `screensRequireSignIn` (open the Sample List signed out → Sign In shown) | 2.4¶4 |
| `happy/SampleRegistrationTest` | `registeredSampleIsListedAsAccessioned` (API patient → screen create → list row by id) | 2.5¶1 |
| `rejection/SampleRegistrationRejectionTest` | `missingFieldIsRequired` (`@ParameterizedTest`: blank patient / blank type) | 2.5¶2 |
|  | `unknownPatientIsRefused` | 2.5¶3 |
|  | `unsupportedSampleTypeIsRefused` | 2.5¶4 |
| `happy/SampleWorkflowTest` | `newSampleStartsAtAccessioned` (8 stages in order, first is current) | 2.6¶1 |
|  | `advancingOnceReachesReadyForProcessing` | 2.6¶2 |
|  | `finalStageOffersReportInsteadOfAdvance` (API `walkTo(RESULT_READY)`, then one advance on screen) | 2.6¶3 |
| `rejection/SampleDetailsRejectionTest` | `unknownSampleIsNotFound` | 2.6¶4 |
| `happy/ReportJourneyTest` | `fullJourneyThroughScreensEndsInReport` (the only screens-only journey: create sample, 7 advances, open report, id matches) | 2.7¶1 |
| `rejection/ReportRejectionTest` | `reportNotReadyBeforeFinalStage` (API-prepared sample) | 2.7¶2 |

That is 14 methods and 16 executions. Each file carries the `// @layer: e2e`, `// @spec: 004-ui-automation-page-objects`, `// @regression` header convention. `junit-platform.properties` in `ui-tests` sets random class and method order, as in `api-tests`.

---

## 3. Impact and Risk Analysis

**System dependencies:**
- Depends on the running lab (screens and `/api`, for data prep), `common` (`LabConfig`, `WorkflowStage`, `SampleType`, `LabMessages` for the exact message texts), and `api-client` (`LabClient`, `LabPreflight`).
- Adds about 208 MB (the Playwright driver jar in `~/.m2`) plus about 550 MB (Chromium and its headless shell) on first setup.
- `./mvnw clean test` becomes slower. Expect tens of seconds for this suite.

| Risk | Mitigation |
| --- | --- |
| The Sample List is shared and grows across runs and checks | Rows are found by the unique sample id only; checks never count rows or rely on the empty state. The "No samples registered yet." message is out of reach once any sample exists, so it isn't checked here. |
| The screens show raw enum text | `WorkflowStage.valueOf` in Page Objects. Documented as current lab behaviour. |
| The password leaks into the trace | Structural pause around the one real-password fill, plus a manual trace inspection before this criterion is marked done (§2.5) |
| First-run silent download of all browsers | Explicit `./mvnw -N exec:java` step in the README, and a clear message if the binaries are missing. Phase 3 CI will cache `ms-playwright` keyed on `playwright.version` and set `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` for the test step. |
| Redirect timing after Advance | `advance()` waits for the status to change (§2.6); no sleeps |
| Headed and headless behave differently | Fixed viewport. Headed is a debugging aid only, not a second supported configuration. |
| Read-only or noexec temp dir breaks driver extraction | One `Playwright` per run. `PLAYWRIGHT_NODEJS_PATH` is the documented escape hatch, but it is not configured by default. |
| JDK 26 locally vs 17 in CI | `maven.compiler.release=17` (unchanged). Maven's Unsafe warnings on JDK 26 are harmless noise. |
| A red `api-tests` stops the reactor before `ui-tests` | Document `-fae` for a full verdict |

---

## 4. Testing Strategy

- **Screen checks** (§2.7) are the deliverable. Each is independent: it creates its own patient and samples, runs in random order, and passes on a second run against the same lab.
- **Guard tests** (plain-text scans in `guard/`, no lab or browser needed, same style as the `api-tests` guards):
  1. The checks (`happy`, `rejection`) contain no `locator(`, `getByRole(`, `"#` selectors, `.navigate(`, or `"/samples"` / `"/login"` literals. Only Page Objects touch the screen (FS 2.2).
  2. Page Objects (`ui/pages/**`) contain no `assertThat`, `Assertions` or `PlaywrightAssertions`. Verdicts live only in checks (FS 2.2).
  3. Nothing in `ui-tests` uses `Thread.sleep` or `waitForTimeout`.
  4. Wiring: `junit-platform.properties` sets random order, checks avoid `BeforeAll`/`AfterAll`/`TestInstance`/`Order`, every check extends `UiTest`, and the pre-flight listener is registered.
- **Unit tests** for `UiCredentials` (resolution order, blank handling, a masked `toString`) and the artifact-folder name sanitiser (including "never contains the password").
- **Manual verification** during `/awos:verify`, as spec 003 did:
  - One-element change and revert: rename one lab id locally, show that exactly one Page Object needs editing, then revert (FS 2.2¶5).
  - Deliberate failure: artifacts appear only for the failing check, and the trace opens and is free of the password (FS 2.9).
  - Headed run.
  - Lab stopped: the pre-flight message appears.
  - Clean-machine run after the one-time install.
- **README:** add a short "Screen checks" part under `## Running Tests` covering the one-time install, both commands, `-Dheaded`, the `MOCK_LAB_*` settings, where failure artifacts land and how to open a trace.
