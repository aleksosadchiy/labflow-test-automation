# Functional Specification: Reliable, Isolated Runs with Settings and a Run Log (Phase 2)

- **Roadmap Item:** Phase 2 — Reliable, Isolated Test Execution (labels and multi-input checks, running checks side by side, reusable test-data recipes) and Configuration & Logging (settings from outside the project, a run log)
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The framework already has two families of checks against the Mock Lab: **workflow checks**, which talk to the lab's connected interface (specification 003), and **screen checks**, which drive its screens in a browser (specification 004). Both run one check at a time, always in full, against one browser type, and the only record of a run is the pass/fail output.

That is fine for a demo and weak for a real team. A real team asks four questions that this framework cannot answer yet:

1. **"Can I run just the important bit?"** Before a merge, an engineer wants a quick confidence run (a **smoke** run), not the whole suite. A nightly job wants everything (a **regression** run). Today there is no way to pick.
2. **"Why does the full run take so long?"** Checks run one after another. Running several at once is the normal way to keep a growing suite fast, and it only works if checks never share or overwrite each other's data.
3. **"Can I point it at another lab or browser without editing code?"** The lab address and access key can already be set from outside. The browser and the target environment cannot.
4. **"What happened during the run that failed in the pipeline?"** When a check fails somewhere the engineer cannot watch, they need a written record of what the framework asked the lab and what the lab answered. In a healthcare-like system that record must never contain access keys, passwords or patient names.

In this specification the **engineer** is whoever runs the framework (in practice the author, or a reviewer). The **lab** is the Mock Lab.

Success is measured by four things:

- The engineer can run a smoke subset, a regression subset, only workflow checks or only screen checks, each with one documented command.
- A full run with checks side by side passes every time across repeated runs, and takes noticeably less time than the same run one check at a time.
- The lab address, access key, browser and environment can all be changed without editing anything in the project.
- After any run, the engineer can read what each check asked the lab and what the lab answered, and the record contains no secrets or patient names.

---

## 2. Functional Requirements (The "What")

### 2.1. Labels for Picking What to Run

- **As an** engineer, **I want** every check to carry labels saying what kind of check it is, **so that** I can run just the subset I need.
  - **Acceptance Criteria:**
    - [x] When a reader opens any check, then it is labelled either as a workflow check ("api") or as a screen check ("ui"), and also as "smoke", "regression" or both.  _(verified 2026-10-05: base classes carry @ApiCheck/@UiCheck + @Regression, 6 methods @Smoke; `LabelSelectionAcceptanceTest#everyCheckResolvesTo{Api|Ui}AndRegression`, `LabelGuardTest` green)_
    - [x] When the engineer runs the documented "smoke" command while the lab is running, then only the checks labelled "smoke" run (together with the framework's own quick self-checks, which need no lab), and every check run passes.  _(verified: `./mvnw test -Dgroups="smoke | !(api | ui)"` → `Lab checks run: 3` in ui-tests and in api-tests, all green, BUILD SUCCESS)_
    - [x] When the engineer runs the documented "regression" command, then every check labelled "regression" runs, which is all workflow and screen checks.  _(verified: `-Dgroups=regression` → `Lab checks run: 16` (ui) + `48` (api), equal to every lab check, BUILD SUCCESS)_
    - [x] When the engineer runs the documented "api" or "ui" command, then only workflow checks or only screen checks run respectively.  _(verified: `-pl api-tests -am -Dgroups=api` → `Lab checks run: 48`; `-pl ui-tests -am -Dgroups=ui` → `Lab checks run: 16`; `LabelSelectionAcceptanceTest` proves no cross-family selection)_
    - [x] When a reader lists the checks labelled "smoke", then they are exactly the key successful paths plus one refusal: the full successful workflow through the connected interface, a request refused for a missing or wrong access key, signing in on screen, and the one full journey through the screens to the report (roughly 5 to 8 checks in total).  _(verified: `LabelGuardTest#smokeChecksAreExactlyTheAgreedOnes` (api 3 + ui 3) and `LabelSelectionAcceptanceTest#smokeCommandSelects…`)_
    - [x] When the engineer asks for a label that no check carries, then the run finishes and its output clearly states that 0 checks ran, so the engineer can see that the label matched nothing.  _(verified: `-Dgroups=nosuch` → `Lab checks run: 0 (labels: nosuch)` in both modules, BUILD SUCCESS)_

### 2.2. One Check, Many Inputs

- **As a** reviewer, **I want** checks that differ only by their input to be written once with a table of inputs, **so that** the suite stays small and every variation is still visible.
  - **Acceptance Criteria:**
    - [x] When a reader looks for checks that repeat the same steps with different inputs (for example, each missing required field, each invalid sample type, each blank sign-in field), then each such group is written as one check with a list of inputs, not as copied checks.  _(verified: InputValidation, BadRequest, DuplicateSample and UI SampleRegistration rejections are `@ParameterizedTest` tables (16 → 23 cases); `PlainRowNamesAcceptanceTest#inputGroupsAreParameterizedChecks`)_
    - [x] When the engineer reads the run output for such a check, then each input appears as its own line with a plain-language name describing that input (for example "a sample without a patient is refused"), and a failure names the exact input that failed.  _(verified: surefire XML lists every row by name (e.g. "A patient whose name is only blank spaces is refused"); `PlainRowNamesAcceptanceTest#everyRowHasAPlainLanguageName`; a broken row fails as `[Scenario: <row name>]` (Slice 4 RED))_

### 2.3. Running Checks Side by Side

- **As an** engineer, **I want** checks to run several at a time, **so that** the full suite stays fast as it grows.
  - **Acceptance Criteria:**
    - [x] When the engineer runs the full suite with default settings, then several checks run at the same time, for both workflow checks and screen checks, and each screen check that is running at the same time has its own separate browser window.  _(verified: default full run used 12 api worker threads and 11 browsers ("Browser started for worker ForkJoinPool-…"), one context per check; `BrowserPerWorkerTest` green)_
    - [x] When the engineer runs the full suite with checks side by side, then it takes less time than with checks one at a time on the same machine, and the README records both measured times. No fixed speed-up is required.  _(verified: regression 6.48/6.26/6.44 s side by side vs 9.10 s with `-DchecksAtOnce=1`; README records 6.470 s vs 9.067 s (median of 3, Slice 8))_
    - [x] When the engineer runs the full suite side by side three times in a row against the same running lab without restarting it, then all three runs pass.  _(verified: `-Dgroups=regression` three times in a row on one lab: 16 + 48 checks, 0 failures each time)_
    - [x] When the engineer starts a run without choosing how many checks run at once, then the run uses as many as the machine has processor cores.  _(verified: `ChecksAtOnceStrategyTest#defaultsToTheNumberOfCores`; default run on a 14-core machine used up to 12 workers)_
    - [x] When the engineer sets the documented "checks at once" option to a number, then no more than that many checks run at the same time.  _(verified: `-DchecksAtOnce=2` → exactly 2 api worker threads and 2 browsers; `ChecksAtOnceStrategyTest#theEngineRunsNoMoreThanTheChosenNumberAtOnce`; `abc` → `checksAtOnce must be a positive whole number, got 'abc'`)_
    - [x] When the engineer sets the "checks at once" option to 1, then checks run one after another and all pass, so a confusing failure can be investigated without other checks running alongside.  _(verified: `-DchecksAtOnce=1` → 1 worker thread, 1 browser, 16 + 48 checks green, 9.10 s)_

### 2.4. Reusable Recipes for Test Data

- **As an** engineer writing a new check, **I want** one shared place that makes valid patients, orders and samples with sensible defaults, **so that** a check only spells out the detail it is actually about.
  - **Acceptance Criteria:**
    - [x] When a reader opens any check that needs a patient, order or sample, then the check gets it from the shared recipes and states only the details that matter to that check (for example "a sample of an unsupported type"); everything else is filled in with valid defaults.  _(verified: `CheckIndependenceGuardTest#checksTakeTheirDataFromTheRecipes` (both modules) bans UUID and hand-written names; checks use `Patients`/`Orders`/`Samples` drafts)_
    - [x] When a reader looks for where test data is made, then they find exactly one shared place used by both workflow checks and screen checks, instead of separate copies in each.  _(verified: single `test-data` module, test scope in api-tests and ui-tests; both local UniqueData copies deleted (Slice 2 Verify))_
    - [x] When any two checks run at the same time, then every patient name, order and sample they create is unique to that check, so neither can see or change the other's data.  _(verified: `UniqueDataTest#manyNamesAreAllDifferent`; full suite green under parallel execution, twice in a row)_
    - [x] When a reader sees a patient created by a check, then the name is obviously made up and marks it as created by the framework (for example it starts with a fixed test prefix), so it can never be mistaken for a real person.  _(verified: `Patients.valid()` → `TEST-patient-<uuid>`; the lab stored `TEST-patient-44eff689-…` (Slice 2 Verify); `UniqueDataTest#nameStartsWithTestPrefixAndKind`)_

### 2.5. Settings from Outside the Project

- **As an** engineer, **I want** to choose the lab address, access key, browser and environment when I start a run, **so that** the same project works on my machine, in the pipeline and against another lab without edits.
  - **Acceptance Criteria:**
    - [x] When the engineer starts a run without any settings, then the run uses the lab's standard local address, its documented local key, the default browser and the "local" environment, and the run passes against a locally started lab.  _(verified: `LabConfigTest#nothingSuppliedUsesTheDefaults`, `BrowserChoiceTest#nothingSuppliedUsesChromium`; banner `environment=local … key=*** browser=chromium`)_
    - [x] When the engineer sets the browser to any of the supported browsers, then the screen checks run in that browser and pass. The supported browsers are Chromium (the default) and WebKit; the extra browser is obtained with one documented, one-time setup command.  _(verified: `-Dbrowser=webkit` → banner `browser=webkit`, 16/16 screen checks green; Chromium is the default run)_
    - [x] When the engineer sets a browser name that is not supported, then the run stops before any check with a message naming the value given and listing the supported names.  _(verified: `-Dbrowser=opera` → `Unsupported browser 'opera'. Supported: chromium, webkit`, ui-tests Tests run 0, BUILD FAILURE)_
    - [x] When the engineer sets the environment name, then the run uses that environment's lab address unless an address is given explicitly, in which case the explicit address wins. The known environments are "local" (the default) and "ci"; an environment supplies only a default lab address and its name in the run output, and nothing else differs between them.  _(verified: `-Denvironment=ci` → banner `environment=ci address=http://localhost:18080`; `LabConfigTest#explicitAddressBeatsTheEnvironment`)_
    - [x] When the engineer sets an environment name that is not known, then the run stops before any check with a message naming the value given and listing the known names.  _(verified: `-Denvironment=staging` → `Unknown environment 'staging'. Known: local, ci`, api-tests Tests run 0)_
    - [x] When any run starts, then its output shows, once, the lab address, browser and environment in use, and shows the access key only as hidden (for example "***").  _(verified after fix: full `clean test` prints exactly 2 banners (one per test family) `Lab run: environment=local address=… key=*** [browser=chromium]`; nested self-test sessions no longer repeat it (see Change Log))_
    - [x] When a reader searches the project's checks and helpers for a lab address, access key or browser name, then none is written into a check; each comes only from the settings.  _(verified: `NoHardcodedSettingsAcceptanceTest#settingsComeOnlyFromTheSettings` (both modules))_

### 2.6. A Run Log That Is Safe to Share

- **As an** engineer investigating a failure I could not watch, **I want** a written record of what each check asked the lab and what the lab answered, **so that** I can find the cause from the record alone.
  - **Acceptance Criteria:**
    - [x] When any workflow check talks to the lab, then the run log records, for each request, which check made it, what was asked for (the kind of request and the lab address path), the answer's status, how long it took, and the identifiers involved (patient, order and sample identifiers).  _(verified: api-tests.log line `DuplicateSampleRejectionTest › … POST /api/patients -> 201 in 69 ms [patient=PAT-…]`; `RequestLogFilterTest`)_
    - [x] When a check fails, then the engineer can find every log line belonging to that check by searching for the check's name.  _(verified: deliberate failure in SampleProgressionTest → 16 lines found by grepping `SampleProgressionTest › <name>` (Slice 5 Verify); `RunLogSecrecyTest`)_
    - [x] When any run finishes, then the access key, the sign-in password and any patient name do not appear anywhere in the run log, in the run output or in the saved reports, whether the checks passed or failed.  _(verified: after a `-DlogBodies=true` full run, 0 hits for the key, `I_Like_This_Lab`, `Authorization`, `TEST-patient-` in logs, console and surefire reports (`Bearer` only in api-client test names with a `<key>` placeholder))_
    - [x] When the engineer runs with default settings, then the console shows a short summary per request, and the full detail goes to one log file per test family (workflow checks, screen checks), kept with that family's run results and removed by the usual clean-up command.  _(verified: console shows one INFO line per request; full detail in `api-tests/target/logs/api-tests.log` and `ui-tests/target/logs/ui-tests.log`, removed by `clean`)_
    - [x] When the engineer runs with the documented "more detail" option, then the log also includes the bodies of requests and answers, with secrets and patient names still hidden.  _(verified: `-DlogBodies=true` → 71 masked bodies `{"name":"***"}` in the file, 0 body lines on the console)_
    - [x] When a reader searches the framework for direct printing to the console, then they find none; everything goes through the run log.  _(verified: `NoConsolePrintingGuardTest#frameworkNeverPrintsToTheConsoleDirectly` green)_

---

## 3. Scope and Boundaries

### In-Scope

- Labels on every workflow and screen check ("api", "ui", "smoke", "regression") and one documented command per subset.
- Grouping checks that differ only by input into one check with a list of inputs, each input named in the output.
- Running workflow and screen checks side by side, with a documented "one at a time" option, proven by repeated runs.
- One shared set of test-data recipes with valid defaults and unique, obviously fake data per check.
- Lab address, access key, browser and environment set from outside the project, with clear early errors for unknown values.
- A run log of what checks asked the lab and what it answered, with secrets and patient names hidden.
- README updates for every new command and option.

### Out-of-Scope

- The automated build pipeline and published test reports in it (**Phase 3 — CI/CD Pipeline**). This specification only makes sure the settings and labels are ready for it, for example the "ci" environment name.
- The full README rewrite and the design walkthrough (**Phase 3 — Documentation & Walkthrough**); only the new commands and options are added here.
- The optional Cucumber demonstration (**Phase 3 — Optional BDD Layer**).
- Any change to the lab or its screens.
- Running checks on several machines at once, cloud browser farms, or running each check in several browsers within one run.
- A real second environment: "ci" may point at the same kind of locally started lab.
- Storing logs anywhere except alongside the run's results, log dashboards, and alerting.
- Adding new checks for lab behaviour that is not already checked; new checks appear here only where a requirement above needs proof.

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- 2026-10-04 — Slice 1, Task 3 finding — Firefox removed from the supported browsers. Playwright's Firefox build (155.0) exits at start-up on this development machine (macOS 27) with "Could not find profile folder", even when started directly without the framework. The engineer chose to support Chromium and WebKit only rather than keep a browser that cannot be proven here.
- 2026-10-04 — Slice 3 Verify finding — removed the criterion "the smoke run finishes in under a third of the regression run's time". The whole suite runs in about 9 seconds, so Maven start-up dominates both runs, and the smoke command also runs the framework's self-checks that regression skips (measured: smoke 9.4 s, regression 8.8 s). The time cannot be compared fairly at this size. The smoke set is still pinned to exactly the listed key checks.
- 2026-10-05 — /awos:verify finding — the settings banner appeared once per launcher session, so self-tests that start a nested JUnit engine repeated it (16 times in a full run). Fixed: the pre-flight listeners announce and probe only for the first session in the JVM. A full run now prints the banner exactly once per test family.
