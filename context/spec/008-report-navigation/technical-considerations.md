# Technical Specification: A Report You Can Find Your Way Around

- **Functional Specification:** [`functional-spec.md`](functional-spec.md)
- **Status:** Completed
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Every lab check becomes a Gherkin scenario in a feature file, run by **Cucumber-JVM** on the same JUnit Platform the project already uses. The framework's own self-checks stay plain Jupiter tests. Nothing below the checks changes: scenarios call the same `api-client` services and the same Page Objects.

The report keeps the tool and the build of spec 007. What changes is who records a check: Allure's **Cucumber adapter** instead of its JUnit Platform listener. That adapter gives each result its Gherkin lines as steps for free; the family, topic and sub-group labels are set in a hook.

This is a rewrite of the check layer, not a report tweak. Jupiter extensions do not apply to Cucumber scenarios, so the browser lifecycle, failure evidence, the check name in the log and the per-check time limit all have to be rebuilt as Cucumber hooks. The plan therefore starts with **one proving slice** that converts a single topic in both families next to the existing checks, deletes nothing, and ends in an explicit go / no-go.

**Versions (verified against Maven Central in this session):**

| Library | Version | Why this one |
| --- | --- | --- |
| Cucumber-JVM (`cucumber-bom`) | **7.34.9** | The latest 7.x. Cucumber 8.0 moved to JUnit 6 (its engine builds against Jupiter 6.1.3); 7.34.9 builds against Jupiter 5.14.2 and needs only `junit-platform-engine`. The same situation as Allure 2.x against 3.0. |
| `allure-cucumber7-jvm` | 2.35.5 (already in the Allure BOM) | The adapter for the Cucumber 7 plugin API. It declares no Cucumber version; the plugin classes it uses exist in 7.34.9. **Not verified:** the pairing at run time. |

**Specialists consulted:** `testing-expert` (versions, discovery, hooks, the adapter's bytecode, the report tool's sorting) and `github-actions-cicd` (Surefire reporting, build wiring, Dependabot, CI). No separate codebase exploration was run; both read the code they reported on.

**Three places where the functional spec could not be met literally** are recorded in section 2.9; the owner accepted the amendments on 2026-10-12.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1. Files

| Location | Change | Responsibility |
| --- | --- | --- |
| root `pom.xml` | edit | `cucumber.version`, the Cucumber BOM import; a Spotless rule for `*.feature` (trailing spaces, final newline). |
| `api-tests/pom.xml`, `ui-tests/pom.xml` | edit | Test scope, no versions: `cucumber-java`, `cucumber-junit-platform-engine`, `cucumber-picocontainer`, `allure-cucumber7-jvm`. |
| `<module>/src/test/resources/features/*.feature` | new | One file per topic and family (about 15 in all). |
| `<module>/src/test/java/…/steps/` | new | Step definitions, one class per topic. |
| `<module>/src/test/java/…/support/` | new / replace | `LabWorld` / `UiWorld` (per-scenario state and the data helpers of today's `LabTest` / `UiTest`), the hook classes, `BrowserPool` (ui). |
| `<module>/src/test/resources/junit-platform.properties` | edit | The Cucumber engine's settings (2.3). |
| the Jupiter classes of the 64 lab checks, `LabTest`, `UiTest`, `ScenarioNameExtension`, `BrowserExtension`, `CheckNameInterceptor`, `LabChecksAllureListener` and their tests | delete, last | Removed only after every scenario is green and counted (2.8). |
| guard and acceptance tests | rewrite | See 2.8. |
| `README.md`, `architecture.md`, `product-definition.md`, `CLAUDE.md`, module READMEs | edit | The check layer, the scenario files, and the reversed "no BDD layer" decision. |

`api-client`, `common`'s config and model, `test-data` and `mock-lab-app` are not touched. `ci.yml` needs no change.

### 2.2. Feature Files, Topics and Names (FS §2.1, §2.2, §2.5)

- **One feature file per topic and family.** The `Feature:` name is the topic, with a description of one or two sentences. Tags on the `Feature:` line (`@api` or `@ui`, plus `@regression`) reach every scenario, because a scenario carries the tags of its feature. `@smoke` goes on the six key scenarios. Each scenario carries `@accepted` or `@refused`.
- **Proposed topics, numbered in workflow order and shared by both families** (the numbering is the subject of 2.9):

  | Topic | Workflow checks | Screen checks |
  | --- | --- | --- |
  | 01. Getting access | access key missing or wrong | sign-in, sign-in refusals |
  | 02. Patients | register, look up, name rules | — |
  | 03. Orders | place, look up, patient rules | — |
  | 04. Registering a sample | register, starts at Accessioned, input rules, duplicates | register on screen, form refusals |
  | 05. Moving through the stages | stage by stage, skipped stages refused | details screen, advancing |
  | 06. Results and reports | result, report, "not ready yet" | report screen, "not ready yet" |
  | 07. Unknown samples | every request about an unknown sample | details of an unknown sample |
  | 08. Malformed and unsupported requests | unreadable content, deleting not offered | — |
  | 09. The whole journey | patient to report | registration to report |

  The exact split is settled in the proving slice and the first conversion slice; no sub-group may exceed 12 scenarios (FS §2.1).
- **Scenario names** are the short names: at most 80 characters, unique within a topic (lowered to 45 on 2026-10-18, see the functional spec's Change Log). Today's long `@DisplayName` text becomes the scenario's description (Gherkin allows free text under the title), which the adapter shows as the check's description.
- **Checks that run once per row** become a `Scenario Outline` with an `Examples` table. The outline title holds a placeholder for a short, readable case name (for example `A sample <case> is refused`), so every row gets its own distinct name.
- **No secret in an `Examples` table.** The adapter records the whole example row as the result's parameters. A column must therefore hold a label ("a blank password"), and the step resolves the label to the value. A guard scans feature files for the documented key and password.

### 2.3. Running the Scenarios (FS §2.3 ¶4, §2.6)

- **Discovery (amended after the proving slice, 2.10).** One `@Suite` class per module (`junit-platform-suite`): `WorkflowScenariosTest` in `api-tests`, `ScreenScenariosTest` in `ui-tests`, each with `@IncludeEngines("cucumber")` and `@SelectPackages("features")`. Surefire finds the class, and every scenario is reported as a test of it, so a failing scenario fails the build. `-Dgroups=<tag expression>`, the pre-flight `LauncherSessionListener` and `CheckCountListener` work through the suite; the documented commands are unchanged.
  - The design's first choice, letting the Cucumber engine find the feature files itself (`cucumber.features=classpath:features`), ran each scenario once but left the scenarios out of Surefire's count and verdict, so it was dropped.
- **Parallel execution.** The Cucumber engine takes a custom strategy through `cucumber.execution.parallel.enabled=true`, `…config.strategy=custom`, `…config.custom.class=com.labflow.common.junit.ChecksAtOnceStrategy`. It expects JUnit's own `ParallelExecutionConfigurationStrategy` (verified in the engine's jar), so `ChecksAtOnceStrategy` and `-DchecksAtOnce=N` work unchanged.
- **Random order.** `cucumber.execution.order=random`.
- **Two engines in one run.** **Not verified:** that the Jupiter self-checks and the scenarios run one after the other. If they overlap, the number of checks running at once could exceed `checksAtOnce`. The proving slice checks it.
- **Naming in Surefire's XML, the console and the summary.** `cucumber.junit-platform.naming-strategy=short` with `…short.example-name=pickle`, so a scenario is reported under exactly its short name and an outline row under its own. `long` would join feature, scenario and row into one name and break the 80-character rule. **Not verified:** the exact strings in `TEST-*.xml`; `junit_summary.py` keeps working, but its "Class" column would repeat one name and should be dropped or renamed.

### 2.4. What Replaces the Jupiter Extensions

| Today (Jupiter) | Becomes (Cucumber) |
| --- | --- |
| `LabTest` / `UiTest` data helpers and fields | `LabWorld` / `UiWorld`: one object per scenario holding the client, the page, the last answer and the unique-data helpers. Shared between step classes by constructor injection (`cucumber-picocontainer`; the default factory cannot share state between step classes). |
| `BrowserExtension` | `UiHooks`: `@Before` opens a context, starts tracing and creates the page; `@After` on failure saves the screenshot and trace, calls `AllureEvidence`, then closes the context. `BrowserPool` keeps one Playwright and Browser per worker thread and a registry closed in Cucumber's global `@AfterAll`. The sign-in pause in tracing (`RecordingPause`, `TraceSettings`) is reused as it is. |
| `ScenarioNameExtension` | Not needed: the scenario's name is the name. Failure folders use the sanitised scenario name, with the example row's line number to keep rows apart. |
| `CheckNameInterceptor` (check name in the log) | A `@Before` puts `Feature › scenario` into the MDC, an `@After` clears it. Cucumber runs hooks and steps on one thread, so the separate-thread problem of spec 005 goes away. |
| JUnit's 2-minute limit in `SEPARATE_THREAD` mode (`ui-tests`) | **No equivalent exists** (2.9). |
| Composed label annotations on base classes | Gherkin tags on the `Feature:` line. `Labels.isLabCheck` keeps working, because the engine exposes Gherkin tags as JUnit Platform tags. |

### 2.5. The Report (FS §2.1, §2.3–§2.5)

- **Recording.** `cucumber.plugin=io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm` in each module's `junit-platform.properties`. The adapter uses the same Allure lifecycle as today, so results still go to the shared `target/allure-results`, and `report.sh` and the Report job are untouched.
- **`LabChecksAllureListener` is deleted.** Once every lab check is a scenario, only the Cucumber adapter records. The line that switches off Allure's JUnit Platform listener stays, which keeps the self-checks out and prevents a scenario being recorded twice.
- **Steps.** The adapter records each Gherkin line as a step. `AllureStepAppender` is unchanged: a request line logged during a Gherkin step nests under that step (the adapter opens the step on the thread's Allure context; verified in bytecode). A failed line is shown failed, later lines skipped.
- **Grouping.** The adapter sets `feature` to the Feature name and no `epic`. A `@Before` hook sets the rest from the scenario's tags: `epic` = family, `story` = sub-group ("Accepted" / "Refused"), and `parentSuite` / `suite` / `subSuite` to the same three values, so the Suites view mirrors the grouped view and is named in plain language (FS §2.4). **Not verified:** replacing the adapter's default `story` label from a hook.
- **Order.** Allure Report 2.46 sorts groups by lower-case name, always; the default setting lives in the reader's browser and there is no build-time option (verified in the tool's bundle). Group order can therefore only come from names (2.9).
- **Evidence and secrecy.** `AllureEvidence` is reused from the new `@After` hook. The secrecy scan of spec 007 is repeated, with the feature files added to it.

### 2.6. Order of Work

1. **Proving slice.** Topic "01. Getting access" in both families as scenarios, running beside the existing Jupiter checks; nothing deleted. It must show, with output: each scenario runs once; `-Dgroups` selects by Gherkin tag; `Lab checks run` counts them; the pre-flight still stops the run; `checksAtOnce=1` and `=4` behave; random order; the check name on log lines; in the report: topic order, steps with nested requests, a forced screen failure with picture and trace, no password, no self-checks; the names in Surefire's XML; the run time.
2. **Go / no-go with the owner**, on that evidence and on 2.9.
3. Conversion, one topic per slice, each ending with the Jupiter checks of that topic deleted and the counts unchanged (48 and 16).
4. Removal of the Jupiter check layer and rewrite of the guards (2.8).
5. Documentation and product notes; timing; feature testing.

### 2.7. Estimated Size

About 15 feature files, 63 scenarios (more result entries, since outline rows expand), 70–90 step definitions, two hook classes, a label hook, `BrowserPool`, two world classes. Hardest to express in Gherkin without the step text turning into code: the run-log secrecy check (its subject is the framework's own log), the input-validation checks (three tables of payload variations), and the journeys with many stage assertions.

### 2.8. Guards and Acceptance Tests

- **Lose their subject and are deleted:** `LabChecksAllureListenerTest`; the tests of `ScenarioNameExtension` and `CheckNameInterceptor`; `BrowserExtensionArtifactsTest`, `BrowserPerWorkerTest`; both `PlainRowNamesAcceptanceTest`. The behaviours they pinned (failure evidence, one browser per worker) need new tests against the hooks.
- **Rewritten against feature files or the engine's settings:** both `LabelGuardTest` and `LabelSelectionAcceptanceTest`, `LabRunWiringTest`, `UiRunWiringGuardTest`, both `CheckIndependenceGuardTest`, `PhrasedTestNamesGuardTest`.
- **Re-pointed at step definitions:** `NoRawHttpInChecksTest`, `FrameworkBoundaryGuardTest`, `NoHardcodedSettingsAcceptanceTest`, `ScreenBoundaryGuardTest`, `NoManualWaitsGuardTest`, `PageObjectVerdictGuardTest` (both `CheckSources` helpers hard-code the `happy` and `rejection` packages).
- **Unchanged:** `NoConsolePrintingGuardTest`, `NoAllureRequestCaptureGuardTest`, `LabLifecycleGuardTest`, the pre-flight tests, `CiWorkflowGuardTest`.
- **New, proportionate:** a dry run (`cucumber.execution.dry-run`) that fails on an undefined or unused step; 48 and 16 scenarios; exactly one family tag per feature and one sub-group tag per scenario; names at most 80 characters and unique per topic; the same topic numbering in both families; no secret in a feature file.

### 2.9. Where the Functional Spec Could Not Be Met Literally

**Decided by the owner on 2026-10-12:** all three amendments below are accepted and written into the functional spec (see its Change Log), and the work starts with the proving slice of 2.6.

| FS criterion | What is possible | Proposed amendment |
| --- | --- | --- |
| §2.3 ¶1–2: topics in workflow order; inside a sub-group, checks in the order they are written in the file | Groups sort by name only. Topics can be ordered by a numeric prefix ("01. Getting access"). Inside a sub-group the entries are alphabetical unless every scenario name carries a prefix too, which would spend characters of the 80 and clutter every name. | Topic names carry a two-digit prefix. Inside a sub-group the order is alphabetical by short name, the same in every report. |
| §2.2 ¶4 and §2.5 ¶4: a row "grouped under the name of the check it belongs to" | The report has three grouping levels and they are used by family, topic and sub-group. There is no fourth level for an outline. | Each row is its own entry with its own short name inside the sub-group. |
| Spec 004's 2-minute limit per screen check (covered by FS §2.6 "nothing else changes") | Cucumber has no per-scenario timeout and nothing like `SEPARATE_THREAD`. Options: (a) rely on Playwright's 30-second waits and Surefire's 10-minute fork limit; (b) a watchdog started in `@Before` that, at 2 minutes, logs a thread dump naming the check and interrupts it; (c) wrap every step in a timed future. | (b) plus the existing fork limit. **A behaviour change the owner must accept:** a call that ignores interruption can no longer be abandoned for one check; it fails the whole run at 10 minutes. |

**Decided by the owner on 2026-10-12:** the run-log secrecy check does not become a scenario. Its subject is the framework's log, so it is rewritten as a self-check that still runs against the lab but carries no family label. The workflow-check count becomes 47 (63 scenarios in all).

### 2.10. Proving Slice: Findings (2026-10-18)

Topic "01. Getting access" ran as scenarios beside the existing checks in both modules (7 workflow scenario entries, 5 screen scenario entries), against a lab on port 18080. Nothing was deleted.

**What held**

- **Each scenario runs once.** Full run: `WorkflowScenariosTest Tests run: 7`, `Lab checks run: 55` (48 + 7); `ScreenScenariosTest Tests run: 5`, `Lab checks run: 21` (16 + 5). The report holds 76 checks.
- **Selection by label works through the suite.** `regression | !(api | ui)` → 21 / 55; `api` → 0 / 55; `ui` → 21 / 0; a label that matches nothing → 0 / 0 and BUILD SUCCESS. The smoke expression gives 4 / 4 (3 Jupiter checks and 1 scenario per module; inferred from the six `@smoke` checks, not run on `main`). The fear of 2.3 that `-Dgroups` would not reach scenarios under a `@Suite` was wrong.
- **Pre-flight.** With the lab stopped, the run stops before any scenario with its one message, `LAB PRE-FLIGHT FAILED: cannot reach the lab at http://localhost:18080. …` (Maven's error summary repeats it three times; it is raised once).
- **Checks at once.** At `checksAtOnce=1` at most one check ran at a time in each module, with no scenario/Jupiter overlap; at `=4`, at most four. The Jupiter checks finish 67–5772 ms before the first scenario starts: the two engines run one after the other. `ChecksAtOnceStrategy` is picked up through `cucumber.execution.parallel.config.custom.class`.
- **Random order.** Two runs at `checksAtOnce=1` executed the scenarios in different orders, in both modules.
- **Check name in the log.** Every run-log line of a scenario carries `01. Getting access › <scenario name>` (19 of 19 workflow lines); outline rows log under their own names. The only unnamed line is `Browsers closed: N`, from the global `@AfterAll` hook.
- **The report.** Families "Screen checks" and "Workflow checks", topic "01. Getting access" in each; sub-groups Accepted (1) / Refused (4) for screens and Refused (7) for the workflow, the empty Accepted not shown. Suite view: `Screen checks / 01. Getting access / Accepted`. Each result has exactly one `epic`, `story`, `feature`, `parentSuite`, `suite` and `subSuite` (the hook replaces the adapter's defaults, addressing the result by `scenario.getId()`). The Gherkin lines appear word for word as steps, with the request lines nested under the step that made them; each outline row is its own entry; no self-check is recorded. A deliberately failed wrong-password scenario showed the `Then` failed and the following `And` skipped, Surefire pointed at `01-getting-access.feature:21`, and the screenshot and trace were saved under `ui-tests/target/ui-failures/chromium/01-getting-access/a-wrong-password-is-refused-and-the-sign-in-screen-stays-16/` and shown in the report. The edit was restored byte for byte.
- **Secrecy.** Neither the access key nor the password is in the result files, the decoded report, the feature files, the trace archive's entries or Surefire's XML (329 and 488 files scanned; a planted key was found, so the scan works).
- **Names.** Surefire's XML: `<testcase name="Registering a patient with a wrong access key is refused" classname="WorkflowScenariosTest">`, exactly the short name; the console: `ScreenScenariosTest.A wrong password is refused and the Sign In screen stays -- <<< FAILURE!`; `junit_summary.py` shows the short name, with its Class column now repeating the suite class (dropped or renamed in Slice 8, as planned).
- **The "not verified" assumptions of 2.3–2.5.** Adapter 2.35.5 with Cucumber 7.34.9 at run time: confirmed. Each scenario once: confirmed, with the suite class. `-Dgroups` under a `@Suite`: works (the fear was refuted). Engines one after the other: confirmed for recorded checks. Names in `TEST-*.xml`: confirmed. Replacing `story` from a hook: confirmed. Request lines nested under steps: confirmed at run time.

**What did not hold as planned**

- **Discovery uses the fallback of 2.3.** Direct discovery was tried once (`-Dcucumber.features=classpath:features '-Dtest=!WorkflowScenariosTest'`): each scenario ran once and `Lab checks run: 55`, but Surefire reported `Tests run: 48`. The scenarios were invisible to Surefire's verdict, so a failing scenario would not fail the build. The `@Suite` class is therefore the plan; 2.3 is amended. Its feared cost, translating `-Dgroups` into `cucumber.filter.tags`, did not arise.

**Open points for the owner and the next slices**

- (a) The failure picture and trace are attached to the after-hook (tear-down) fixture, not to the scenario result itself. They are shown inside the failed check, but one level down.
- (b) Each scenario's set-up and tear-down show hook method names from the code, for example `com.labflow.ui.tests.hooks.UiHooks.closeThePage(io.cucumber.java.Scenario)`, and the Packages tab shows `features.01-getting-access_feature.01. Getting access`. Both go against FS §2.1 "no name taken from the code".
- (c) While the conversion lasts, Jupiter checks sit as direct entries next to the topic in the same family tree. This ends when the last Jupiter lab check is deleted (Slice 7).
- (d) A scenario's description in the report is the feature's description followed by the scenario's description.
- **Not verified:** whether self-checks overlap scenarios (Allure does not record self-checks, so it was not measured); the report rendered in a browser (checked on the decoded data only); a failing `Given`; the 2-minute watchdog (Slice 2).

**What it costs**

- `./mvnw test -Dgroups=regression`, a warm-up and three alternating runs, median, `main` from a worktree of `7589d64`: `main` 33.991 s, branch 35.098 s, **+1.107 s (+3.3%)** with 12 scenario entries added. The limit is 10%.

**Recommendation:** go. Discovery, selection by label and the Allure adapter all work, so nothing calls for narrowing the spec. Caveats (a), (b) and (d) are to be handled, or accepted as amendments, in the conversion slices; (c) goes away by itself; the time limit is Slice 2.

**Owner's decision:** go, 2026-10-18. Slice 2 may start.

---

## 3. Impact and Risk Analysis

**System dependencies**

- The whole check layer of `api-tests` and `ui-tests`, and most guard tests.
- The product notes: `product-definition.md` lists a Cucumber/BDD layer as a non-goal, `architecture.md` says "No BDD layer", `CLAUDE.md` says it was deliberately left out. All three change (`/awos:product`, `/awos:architecture`).
- Dependabot: the existing "ignore major versions" rule keeps it from proposing Cucumber 8. 7.34.9 may be the last 7.x release, so few updates will arrive.

**Risks and mitigations**

| Risk | Mitigation |
| --- | --- |
| Surefire runs the scenarios more than once, or not at all, or leaves them out of its verdict. | Settled in the proving slice (2.10): one `@Suite` class per module; `-Dgroups` selects scenarios through it. |
| `-Dgroups` does not select scenarios, so the documented commands and the CI tag expressions break. | Proving slice, with the exact CI expression `regression \| !(api \| ui)` and with a label that matches nothing. |
| The Allure adapter and Cucumber 7.34.9 do not work together at run time. | Proving slice. |
| The two engines overlap and exceed `checksAtOnce`, starting more browsers than intended. | Proving slice measures concurrent checks at `checksAtOnce=1`. |
| A password or key reaches the report through an example row's parameters. | Labels in tables, a guard on feature files, and the spec 007 scan repeated. |
| The loss of the per-check time limit turns one hung screen check into a 10-minute red run. | The watchdog of 2.9; documented as a known limitation. |
| A step definition quietly does less than the check it replaces, so coverage shrinks while the count stays the same. | Conversion topic by topic with both versions run side by side once; the scenario's requests (the run log lines) are compared with the old check's before the old check is deleted. |
| The suite becomes more than 10% slower (FS §2.6). | Measured in the proving slice against the current 34.2 s; levers: `cucumber.glue` limited to one package, no extra plugins. |
| The size of the change is out of proportion for a small reference project whose brief ranks Cucumber last and lists it under "what not to build". | Go / no-go after the proving slice; the functional spec can be narrowed to the main journeys without touching its other requirements. |

---

## 4. Testing Strategy

- **Per conversion slice:** the topic's scenarios green; the module's `Lab checks run: N` unchanged; the requests each scenario makes equal to those of the check it replaces; the report inspected for that topic.
- **Guards:** as listed in 2.8, each shown red against a deliberate break.
- **New unit tests against the hooks** for what the deleted extension tests covered: failure evidence saved and attached, nothing kept for a passing scenario, one browser per worker.
- **Whole-feature pass:** the spec 007 secrecy scan with feature files included; counts and verdicts against Surefire; two reports of the same run compared for order; the documented commands, including a label that matches nothing; CI green with the report artifact.
- **Rendered evidence** for the look-and-feel criteria, as in spec 007: screenshots of the grouped view, the suite view, an opened workflow scenario and a failed screen scenario.

---

## 5. Hand-off Notes for `/awos:tasks`

- The first slice is the proving slice of 2.6 and ends with a decision, not with deleted code.
- Agents: `testing-expert` for feature files' tests, hooks' tests and guards; `general-purpose` for step definitions, hooks and world classes (framework code); `github-actions-cicd` for Maven wiring, Spotless and the summary script. No Cucumber specialist exists in the registry (`hired-agents.md` recorded that gap earlier); `/awos:hire` can be tried before planning.
