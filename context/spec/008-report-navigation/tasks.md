# Tasks: A Report You Can Find Your Way Around

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

After each slice:
- the branch `spec/report-navigation` builds locally with `./mvnw`, `./mvnw spotless:check` is clean, and every check, old or new, passes against a running lab;
- the lab-free modules (`common`, `test-data`, `api-client`) are green without a lab;
- nothing under `mock-lab-app/`, `api-client/src/main` or `test-data/` changes.

**Working conventions (used by every task):**

- **Lab.** Start one with `LAB_PORT=18080 .github/scripts/lab.sh start`, run with `-DbaseUrl=http://localhost:18080`, stop it with `lab.sh stop`. Never touch a lab on 8080.
- **Maven.** `./mvnw` from the repo root, never `-q` when reading `Tests run` lines, never `install`. No `timeout` binary: run long commands in the background with a capped polling loop, note `date +%T` before and after, wrap long runs in `caffeinate -i` (a sleeping Mac once tripped Surefire's fork timeout), and stop a run that exceeds 3 minutes after capturing `jstack`.
- **Counts.** `Lab checks run: N` is 48 for `api-tests` and 16 for `ui-tests` on `main`. It stays 48 until Slice 7, where the run-log secrecy check becomes a self-check by the owner's decision and the number becomes 47. During a conversion slice both the old check and its scenario run, so the number is higher by exactly the scenarios added and not yet de-duplicated; every slice says what it must be at its end.
- **Parity before deletion.** A Jupiter check is deleted only after its scenario was shown to make the same requests to the lab (the run log's request lines, compared by method, path and status) or the same screen actions, and to expect the same outcome.
- **Report.** Build it only with `.github/scripts/report.sh`, straight after the run. Its data is base64-encoded inside `index.html`: decode before searching.
- **Secrets.** The lab's documented local key and password are the only values to scan for; never print them. A made-up secret in a test starts with `s3cret-`. No secret value in a feature file: tables hold labels.
- **Names.** A scenario's name is at most 45 characters (80 until 2026-10-18; lowered so no name is cut off at 1440 px) and unique in its topic. Topic names start with the two-digit number of `technical-considerations.md` §2.2.
- **Evidence.** Quote real output for every claim. Say plainly what was not verified.
- **Clean-up.** Delete only your own scratch files, by literal path. Stop any lab you started. Screenshots made for a Verify task are deleted at its end, except the ones the final slice keeps.
- **Agent split.** `testing-expert`: feature files, step definitions, hooks, world classes, guards and all tests (everything under `src/test`). `general-purpose`: code under `src/main` and the documentation. `github-actions-cicd`: Maven wiring, Spotless, scripts and the workflow.

---

- [x] **Slice 1: Proving Slice — One Topic as Scenarios, Nothing Deleted**

  > Topic "01. Getting access" exists as scenarios in both families and runs beside the existing checks. The slice proves the risky assumptions of TS §2.3–§2.5 with output and ends in the owner's go / no-go. (TS §2.6 step 1; FS §2.1, §2.5, §2.6 in part)

  - [x] Task: Maven wiring: `cucumber.version` 7.34.9 and the Cucumber BOM in the root `pom.xml`; in `api-tests` and `ui-tests`, test scope and no versions, `cucumber-java`, `cucumber-junit-platform-engine`, `cucumber-picocontainer`, `allure-cucumber7-jvm`; a Spotless rule for `src/test/resources/**/*.feature` (trailing spaces, final newline). Show the dependency trees and that Dependabot's major-version ignore covers Cucumber 8 (comment only). **[Agent: github-actions-cicd]**
  - [x] Task: `api-tests`, topic "01. Getting access" as scenarios: the feature file (family and sub-group tags, an outline for the per-row access-key checks with a readable case name and no secret in its table), step definitions, `LabWorld`, the hooks that put the check name into the log context and set the report's family, topic and sub-group labels, and the Cucumber settings in `junit-platform.properties` (discovery, parallel through `ChecksAtOnceStrategy`, random order, naming, the Allure plugin). The existing Jupiter checks stay untouched. **[Agent: testing-expert]**
  - [x] Task: `ui-tests`, topic "01. Getting access" as scenarios: the feature file (sign-in and its refusals), step definitions on the existing Page Objects, `UiWorld`, `BrowserPool` (one Playwright and Browser per worker, closed once at the end) and `UiHooks` (a fresh context and page per scenario, tracing with the sign-in pause, a screenshot and trace on failure handed to `AllureEvidence`, the failure folder named after the scenario). The existing Jupiter checks and `BrowserExtension` stay untouched. **[Agent: testing-expert]**
  - [x] Verify (lab on 18080), quoting output for each point; delete any screenshot made for it at the end:
    - Each scenario runs exactly once in a full run; `Lab checks run` rises by exactly the number of scenario entries added, in each module.
    - `-Dgroups="regression | !(api | ui)"`, `-Dgroups=api`, `-Dgroups=ui`, the smoke expression and a label that matches nothing select what they should; the last stays green.
    - With the lab stopped, the pre-flight stops the run before any scenario, with its one message.
    - `-DchecksAtOnce=1` and `=4`: scenarios never overlap at 1; say whether Jupiter checks and scenarios ever run at the same time.
    - Two runs execute the scenarios in different orders.
    - Every run-log line of a scenario carries its name.
    - The report: the two families; the numbered topic in each; the sub-groups; each scenario's Gherkin lines as steps with the request lines nested under them; each outline row its own entry with its own name; no self-check; the suite view named in plain language. A deliberately failed sign-in scenario shows its picture and trace; the edit is restored.
    - Neither the access key nor the password is in the result files, the decoded report or the feature files.
    - The names of the scenarios in Surefire's `TEST-*.xml`, in the console output and in `junit_summary.py`'s output.
    - `./mvnw test -Dgroups=regression` timed with the README's method against `main`.
    - A list of every assumption of TS §2.3–§2.5 marked "not verified", each now confirmed or refuted.

    **[Agent: testing-expert]**
  - [x] Checkpoint: write the Verify findings into `technical-considerations.md` (a "Proving slice: findings" section: what held, what did not, what it costs). **The plan stops here: Slice 2 starts only after the owner has recorded "go" in that section.** If discovery, tag selection or the Allure adapter did not work, record the fallback tried and recommend narrowing the spec instead of going on. **[Agent: general-purpose]**

- [x] **Slice 2: The First Topic Stands on Scenarios Alone, with a Time Limit**

  > Topic 01's Jupiter checks are gone, the counts are back to 48 and 16, and a hung screen scenario is named and interrupted at 2 minutes. (FS §2.5 ¶3, §2.6 ¶1, ¶7; TS §2.4, §2.9)

  - [x] Task: A watchdog for screen scenarios: started when a scenario starts, cancelled when it ends; at 2 minutes it logs the scenario's name and a thread dump and interrupts the scenario's thread. Unit tests for it (fires, does not fire, names the scenario), each shown red against a deliberate break. **[Agent: testing-expert]**
  - [x] Task: Tests for what the old extension tests pinned, now against the hooks: a failed scenario leaves a screenshot and a trace in a folder named after it and attaches both; a passing one leaves nothing; one browser per worker thread. Each shown red against a deliberate break. **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): show parity for every topic-01 check (the "Parity before deletion" convention), delete the replaced Jupiter check classes of topic 01 in both modules, and show `Lab checks run: 48` and `16`, a green full run, and the topic complete in the report. A screen scenario made to hang on purpose is named in the output at 2 minutes; restore the edit. **[Agent: testing-expert]**

- [x] **Slice 3: Patients and Orders**

  > Topics "02. Patients" and "03. Orders" (workflow checks only) are scenarios; their Jupiter checks are gone. (FS §2.1, §2.2, §2.5)

  - [x] Task: The two feature files and their step definitions, with the long descriptions of today's checks as each scenario's description and outlines for the per-row input rules. **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): parity for every replaced check; delete the replaced Jupiter classes or methods; `Lab checks run: 48`; the two topics in the report with at most 12 entries per sub-group and names of at most 80 characters. **[Agent: testing-expert]**

- [x] **Slice 4: Registering a Sample**

  > Topic "04. Registering a sample" is scenarios in both families, including duplicates and the input rules. (FS §2.1 ¶3, §2.2, §2.5 ¶4)

  - [x] Task: `api-tests`: the feature file and step definitions (registration, starts at Accessioned, input rules, duplicate types). **[Agent: testing-expert]**
  - [x] Task: `ui-tests`: the feature file and step definitions (registration on screen, form refusals). **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): parity; delete the replaced Jupiter checks; `Lab checks run: 48` and `16`; the topic has the same name and number in both families in the report. **[Agent: testing-expert]**

- [x] **Slice 5: Moving Through the Stages**

  > Topic "05. Moving through the stages" is scenarios in both families. (FS §2.5 ¶6–7)

  - [x] Task: `api-tests`: the feature file and step definitions (stage by stage, skipped stages refused), using data tables where a scenario checks many stages. **[Agent: testing-expert]**
  - [x] Task: `ui-tests`: the feature file and step definitions (the Details screen, advancing, the final stage). **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): parity; delete the replaced Jupiter checks; counts 48 and 16; a failed line in a multi-step scenario shows the lines before it passed and the lines after it not run (restore the edit). **[Agent: testing-expert]**

- [x] **Slice 6: Results, Reports and Unknown Samples**

  > Topics "06. Results and reports" and "07. Unknown samples" are scenarios in both families. (FS §2.1, §2.5)

  - [x] Task: `api-tests`: the two feature files and step definitions. **[Agent: testing-expert]**
  - [x] Task: `ui-tests`: the two feature files and step definitions. **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): parity; delete the replaced Jupiter checks; counts 48 and 16. **[Agent: testing-expert]**

- [x] **Slice 7: Malformed Requests, the Whole Journey, and the Last Jupiter Check**

  > Topics "08. Malformed and unsupported requests" and "09. The whole journey" are scenarios; no lab check is left as a Jupiter test. (FS §2.5 ¶3)

  - [x] Task: `api-tests`: the feature files and step definitions for topics 08 and 09. The run-log secrecy check does NOT become a scenario (owner's decision, 2026-10-12): rewrite it as a self-check of the framework that still runs against the lab but carries no family label, so it leaves the report and the lab-check count. Show it red against a deliberate leak into the log. **[Agent: testing-expert]**
  - [x] Task: `ui-tests`: the whole-journey feature file and step definitions. **[Agent: testing-expert]**
  - [x] Verify, then delete (lab on 18080): parity; delete the last Jupiter lab checks; `Lab checks run: 47` and `16`, and the secrecy self-check still runs and passes; `grep` shows no lab check class left under the old `happy` and `rejection` packages; all nine topics in the report, numbered in order, identical in two reports of two runs. **[Agent: testing-expert]**
  - [x] Task (moved forward from Slice 8 by the owner, 2026-10-18): deleting the last Jupiter lab checks left 16 guards without a subject (they scan the `happy` / `rejection` packages or expect Jupiter checks). Rewrite those guards, and any guard that now silently scans nothing, against feature files, step definitions and the engine's settings, each shown red against a deliberate break, so the run is green at the end of this slice. **[Agent: testing-expert]**

- [x] **Slice 8: The Old Check Layer Is Removed and the Rules Are Pinned Again**

  > Nothing of the Jupiter check layer is left, and guards protect the new one. (TS §2.4, §2.5, §2.8)

  - [x] Task: In `common`, delete `LabChecksAllureListener` and `CheckNameInterceptor` with their tests, and the `allure-junit-platform` dependency if nothing else needs it; keep `Labels`, `ChecksAtOnceStrategy` and `CheckCountListener`. Update `package-info.java`. **[Agent: general-purpose]**
  - [x] Task: In `api-tests` and `ui-tests`, delete `LabTest`, `UiTest`, both `ScenarioNameExtension`s, `BrowserExtension` and the tests that lost their subject; rewrite the remaining guards of TS §2.8 against feature files, step definitions and the engine's settings (the ones that lost their subject were rewritten in Slice 7); add the new guards (a dry run that fails on an undefined or unused step; 47 and 16; one family tag per feature and one sub-group tag per scenario; names at most 80 characters and unique per topic; the same topic numbers in both families; no secret in a feature file). Each guard shown red against a deliberate break. **[Agent: testing-expert]**
  - [x] Task: `junit_summary.py`: drop or rename the column that would now repeat one name on every row; keep the failing check's short name and first message line. Check `ci.yml` needs no change and say so. **[Agent: github-actions-cicd]**
  - [x] Verify (lab on 18080): a green full run with 47 and 16; the lab-free modules green; no reference to a deleted class anywhere (`grep`); the spec 007 secrecy scan repeated with the feature files included; the report unchanged in content from the end of Slice 7. **[Agent: testing-expert]**

- [x] **Slice 8b: One Layout for Both Check Modules** (added by the owner, 2026-10-18)

  > `api-tests` and `ui-tests` share one folder layout that shows where everything is: the runner, one step class per feature file (named after it), hooks, the world, the framework's machinery, and the framework's own self-checks, each in its own place. Moves and renames only; behaviour, counts (47 and 16) and the report are unchanged.

  - [x] Task: In both modules, move test classes into `steps/` (one class per feature, named like the feature: `GettingAccessSteps` for `01-getting-access.feature`, no `Screen` suffix), `hooks/`, `world/` (`LabWorld` / `UiWorld`), `framework/` (browser, evidence, preflight, watchdog, log support — by concern), and `selfcheck/` (`guards/` with their helpers, and `framework/` for the tests of the machinery); the runner class stays at the module's test-package root. Use `git mv`; update packages, imports, `cucumber.glue`, `META-INF/services`, the guards' source paths and the wiring guards; tests of `src/main` code stay mirrored on `src/main`. The feature files move to their own folder `src/test/features/` (declared in the pom as a test resource, so the classpath path `features/` and the commands stay the same); `src/test/resources/` keeps only configuration. Show every guard still scans something and the dry run is green. **[Agent: testing-expert]**
  - [x] Verify (lab on 18080): a green full run with 47 and 16; lab-free modules green; `-Dgroups` selections unchanged; the report's 63 entries unchanged from Slice 8; no file left in an old package (`git grep` of the old package names); the tree of both modules shown side by side. **[Agent: testing-expert]**

- [x] **Slice 9: Documented, Timed and Recorded**

  > The README explains the scenarios and the report's organisation; the product notes record the reversed decision; the run is within 10%. (FS §2.6 ¶8, §2.7)

  - [x] Task: README: the topics of each family with one sentence each, how to read an opened check, where the scenario files are and how to add a scenario, the changed "Run log" example, the time-limit limitation; the module READMEs. Keep the README free of the strings its guard forbids. **[Agent: general-purpose]**
  - [x] Task: Product notes: `architecture.md` (the check layer, Cucumber 7.x and why, hooks instead of extensions, what recording the report now means, the time limit), `product-definition.md` and `CLAUDE.md` (the behaviour-driven layer is now part of the project, why the earlier decision was reversed and when), `hired-agents.md`. **[Agent: general-purpose]**
  - [x] Verify: `./mvnw test -Dgroups=regression` timed against `main` with the README's method, at most 10% slower; every path, command and topic name the README mentions exists; the README followed word for word on a clean checkout for one scenario added to an existing topic and then removed. **[Agent: testing-expert]**

- [x] **Slice 10: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.
  - [x] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 008-report-navigation` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [x] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**

- [x] **Slice 10b: Names That Fit, Plain Set-up Names, the Report Test in CI** (added by the owner, 2026-10-18)

  > Slice 10's rendered check found FS §2.2 not met: 33 of 63 names are cut off at 1440 px, partly because each entry also shows its example row in grey; set-up and tear-down entries show hook method names (FS §2.1); and the report-content acceptance test only runs after a full run. Owner's decisions: shorten the names, give the fixtures plain names, run the report test in CI.

  - [x] Task: Measure the list pane's width at 1440 px and derive the longest name that fits without an ellipsis; stop showing each entry's example row beside its name (set in the hooks); shorten every scenario name over that limit in both families (plain language, unique per topic, the longer text kept in the description, topics unchanged), lower the name limit in `FeatureFilesGuardTest` and the acceptance tests to it, and record the new limit as an amendment in the functional spec's Change Log; give the set-up and tear-down entries plain names instead of hook method names, or report why the adapter does not allow it. README updated where it states the limit. Rendered at 1440 px: no name cut off. **[Agent: testing-expert]**
  - [x] Task: Run `Spec008ReportContentAcceptanceTest` in CI after the report's results are gathered (one step in the report job) and document the local post-run command in the README. **[Agent: github-actions-cicd]**

- [x] **Slice 10c: Plain Family Names and Tidy Descriptions** (added by the owner during `/awos:verify`, 2026-10-18)

  > The owner found the family names "Workflow checks" and "Screen checks" unclear and chose "API tests" and "UI tests"; descriptions in the report break mid-sentence because the feature file's line breaks are kept.

  - [x] Task: Rename the two families in the report (the epic, parentSuite and any label the hooks set) and in the CI run-page summary titles to "API tests" and "UI tests"; join each description's lines into paragraphs before it goes into the report (a blank line stays a paragraph break); update the guards and acceptance tests that pin the old names, the README, and add a Change Log entry to the functional spec. Each changed assertion shown red against a deliberate break; rendered at 1440 px. **[Agent: testing-expert]**
