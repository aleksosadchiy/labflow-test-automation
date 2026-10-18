# Functional Specification: A Report You Can Find Your Way Around

- **Roadmap Item:** None. The roadmap is complete; this builds on the visual report added in spec 007.
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The visual report of spec 007 shows every lab check, but it is hard to read. Its two groups, "Workflow checks" and "Screen checks", are each one long flat list: 48 checks in one, 16 in the other. The checks appear in a different order after every run. Their names are long: half of them are longer than 144 characters, and 51 of the 64 are longer than 100, so in the list most names are cut off before they say what the check is about. A reader who wants to know "is registering a sample covered, and what happens when the patient is unknown?" has to scroll and expand names one by one.

One part of the report already works the way the owner wants: the view that groups checks by suite. There a reader opens one suite and finds a handful of related checks with the lab's answers. But that view names each suite by its place in the code, not in plain language.

This specification makes the report easy to find your way around. Checks are sorted into a small number of named groups by what they are about. Every check has a short name that says its point, with the fuller explanation one click away. Groups and checks appear in the same meaningful order after every run. Every check is written as a short scenario in plain language (given, when, then) in a text file that a non-developer can read, and the report shows the scenario exactly as it is written there.

**A decision this reverses.** The project deliberately left a behaviour-driven layer out (recorded on 2026-10-11, following the project brief's advice that it is optional and lowest in priority). On 2026-10-12 the owner chose to add one after all, for every lab check, because readable scenarios are what makes the report understandable. The project's product notes must be updated to match.

In this specification the **reader** is whoever opens the report (the author or a reviewer). The **report** is the visual report of spec 007. A **check family** is the workflow checks or the screen checks. A **topic** is a named group of checks about one thing the lab does (for example registering a sample). A **scenario** is one check, written as a short story of given, when and then. A **scenario file** is the plain-text file that holds the scenarios of one topic.

Success is measured by four things:

- A reader can name the topic a check belongs to and find it in the report in three clicks or fewer.
- No check name is cut off in the list on an ordinary laptop screen.
- Two reports of the same checks list them in the same order.
- A reader who has never seen the code can say, from a scenario file or an opened check alone, what was set up, what was done and what was expected.
- Every lab check is a scenario in a scenario file; none is left described only in code.

---

## 2. Functional Requirements (The "What")

### 2.1. Checks Sorted into Named Topics

- **As a** reader, **I want** the checks grouped by what they are about, **so that** I can go straight to the part of the lab I care about.
  - **Acceptance Criteria:**
    - [x] When the reader opens the report's grouped view, then they see the two check families, and inside each family a short list of named topics instead of one flat list of checks.  _(verified 2026-10-18: grouped view: two families, numbered topics — docs/screenshots/008-report-navigation-grouped-view.png)_
    - [x] When the reader reads the list of topics, then each topic is named in plain language after something the lab does (for example "Registering a sample" or "Signing in"), with no name taken from the code.  _(verified 2026-10-18: topics named after what the lab does (01. Getting access … 09. The whole journey); Spec008ScenarioFilesAcceptanceTest#noNameInTheScenarioFilesIsTakenFromTheCode)_
    - [x] When the reader compares the two families, then a topic that both families cover has the same name in both (for example "Registering a sample" under the workflow checks and under the screen checks).  _(verified 2026-10-18: all 6 UI topics identical to their API topic (DOM check; FeatureFilesGuardTest#everyScreenTopicIsAlsoAWorkflowTopic))_
    - [x] When the reader opens a topic, then they see two sub-groups, one for the checks where the lab accepts what is asked and one for the checks where it refuses, and a sub-group with no check is not shown.  _(verified 2026-10-18: Accepted before Refused, empty sub-group hidden — docs/screenshots/008-report-navigation-topic-subgroups.png)_
    - [x] When the reader opens a sub-group, then it lists at most 12 checks, all about that topic.  _(verified 2026-10-18: largest sub-group 8 (API 04 Refused); Spec008ScenarioFilesAcceptanceTest#noSubGroupHoldsMoreThanTwelveEntries)_
    - [x] When the reader counts the checks across all topics, then every lab check appears in exactly one topic and none is missing or listed twice.  _(verified 2026-10-18: 63 distinct entries (47 + 16) = 63 expanded scenarios; Spec008ReportContentAcceptanceTest#everyScenarioIsInTheReportExactlyOnceWithItsDescription)_
    - [x] When the reader looks at a topic, then they see how many of its checks passed and how many failed without opening it.  _(verified 2026-10-18: passed/failed badges per topic — 008-report-navigation-grouped-view.png, 008-report-navigation-failed-screen-check.png (1 red, 2 green))_

### 2.2. Short Names That Say the Point

- **As a** reader, **I want** every check to have a short name, **so that** I can scan a list and see what each check is about.
  - **Acceptance Criteria:**
    - [x] When the reader looks at any list of checks in the report, then every check's name is at most 45 characters long.  _(verified 2026-10-18: longest name 45 in Behaviors, Suites and Packages; FeatureFilesGuardTest)_
    - [x] When the reader views the list on a 1440-pixel-wide screen, then no check name is cut off with an ellipsis.  _(verified 2026-10-18: overflowing names at 1440×900: 0 in Behaviors, Suites and Packages (scrollWidth vs clientWidth))_
    - [x] When the reader opens a check, then they see a fuller description of what it checks and why, in addition to its short name.  _(verified 2026-10-18: description shown as paragraphs — 008-report-navigation-workflow-check-opened.png)_
    - [x] When a check runs once per row of data, then each row appears in the sub-group as its own entry under its own short name, and the name of the check and the name of the row are not joined into one long name.  _(verified 2026-10-18: outline rows as own entries — 008-report-navigation-outline-rows.png (API 01 Refused, 7 rows))_
    - [x] When the reader reads the names in one topic, then no two checks in that topic have the same name.  _(verified 2026-10-18: names unique per topic (DOM; FeatureFilesGuardTest#scenarioNamesAreUniqueWithinATopic))_
    - [x] When a check is named anywhere else (the console output of a run, the run log, the run-page summary of an automatic run, and the folder that keeps a failed screen check's picture and recording), then it carries the same short name as in the report.  _(verified 2026-10-18: same short name in console, run log, junit_summary.py and the failure folder (slug + line, accepted by the owner))_

### 2.3. The Same Meaningful Order Every Time

- **As a** reader, **I want** topics and checks listed in a sensible, stable order, **so that** the report reads like the lab's workflow and two reports can be compared.
  - **Acceptance Criteria:**
    - [x] When the reader opens a family, then its topics are listed in the order of the lab's workflow (access and sign-in first, then patient, order, sample, moving through the stages, result and report), and each topic's name starts with its two-digit number in that order, the same number in both families (for example "04. Registering a sample").  _(verified 2026-10-18: topics 01–09 ascending in both families (DOM))_
    - [x] When the reader opens a topic, then the sub-group of accepted checks comes before the sub-group of refused checks, and inside a sub-group the checks are listed alphabetically by their short names.  _(verified 2026-10-18: Accepted before Refused, alphabetical inside (DOM))_
    - [x] When the engineer runs the same checks twice and builds a report after each run, then both reports list the topics and the checks in the same order.  _(verified 2026-10-18: two runs, two reports: rendered (family, topic, sub-group, name) order identical (diff exit 0))_
    - [x] When the engineer runs the checks, then they still run side by side and in a different order each time, as before; only the order shown in the report is fixed.  _(verified 2026-10-18: two runs started scenarios in different orders (60 of 63 positions differ), up to 14 at once)_

### 2.4. Suites Named in Plain Language

- **As a** reader, **I want** the suite view to use plain names too, **so that** the view I already like is readable without knowing the code.
  - **Acceptance Criteria:**
    - [x] When the reader opens the report's suite view, then every suite is named in plain language and none is named by its place in the code.  _(verified 2026-10-18: Suites view in plain names — 008-report-navigation-suites-view.png)_
    - [x] When the reader opens a suite, then its checks appear under the same short names as in the grouped view.  _(verified 2026-10-18: Suites and Behaviors leaf names equal (63))_
    - [x] When the reader opens a workflow check from the suite view, then they still see the requests it made to the lab and the lab's answers, as today.  _(verified 2026-10-18: requests with status shown from the Suites view — opened from the Suites view in the verification run)_

### 2.5. Every Check Is a Scenario in Plain Language

- **As a** reader, **I want** every check written as a scenario I can read without code, **so that** I understand what the lab is expected to do and can review it.
  - **Acceptance Criteria:**
    - [x] When the reader opens the project's scenario files, then they find one file per topic and family, each starting with the topic's name and one or two sentences on what the topic covers.  _(verified 2026-10-18: 15 files (9 API, 6 UI), each Feature: <topic> + 1–2 sentences; Spec008ScenarioFilesAcceptanceTest)_
    - [x] When the reader reads a scenario, then it has the check's short name as its title and a few lines that each start with Given, When, Then or And, written in plain language with no name taken from the code.  _(verified 2026-10-18: Given/When/Then/And lines, no code names; Spec008ScenarioFilesAcceptanceTest#everyScenarioReadsAsGivenWhenThen)_
    - [x] When the reader counts the scenarios in all scenario files, then there are as many as there are lab checks (63: 47 workflow checks and 16 screen checks), and no lab check exists that is not written as a scenario.  _(verified 2026-10-18: 63 scenarios = 47 + 16 lab checks; FeatureFilesGuardTest counts; no Jupiter lab check left)_
    - [x] When a check runs once per row of data, then it is written as one scenario with a table of examples, and the report lists one entry per row of that table, each directly in the sub-group.  _(verified 2026-10-18: outlines with Examples, one report entry per row — 008-report-navigation-outline-rows.png)_
    - [x] When the reader opens a check in the report, then its steps are the lines of its scenario, word for word and in the same order.  _(verified 2026-10-18: steps equal scenario lines for all 63 results; Spec008ReportContentAcceptanceTest#theStepsAreTheScenarioLinesWordForWord)_
    - [x] When the reader opens a workflow check in the report, then each request to the lab is shown under the scenario line that made it, in order, with the details it shows today.  _(verified 2026-10-18: 206 request lines nested under their Gherkin line, 0 at top level; Spec008ReportContentAcceptanceTest#requestsAreNestedUnderTheLineThatMadeThem)_
    - [x] When a check fails, then the report shows which scenario line failed, the lines before it as passed and the lines after it as not run.  _(verified 2026-10-18: failed line red, earlier passed, later skipped — 008-report-navigation-failed-workflow-line.png, 008-report-navigation-failed-screen-check.png)_
    - [x] When the reader opens a screen check that failed, then its picture and its recording are still shown inside it.  _(verified 2026-10-18: picture and Playwright trace in the failed UI test — 008-report-navigation-failed-screen-check.png)_
    - [x] When a scenario contains a line that the framework does not know how to carry out, then the run fails with a message naming that line and its file, and the scenario is not counted as passed.  _(verified 2026-10-18: UndefinedStep … features/09-the-whole-journey.feature:14, BUILD FAILURE; DryRunGuardTest)_

### 2.6. Nothing Else Changes

- **As an** engineer, **I want** the reorganisation to leave the checks themselves alone, **so that** a tidier report cannot hide a real change.
  - **Acceptance Criteria:**
    - [x] When the engineer runs the whole suite before and after this change, then the same lab checks run in each family and every check has the same verdict, with one exception: the check that the run log never holds a patient's name or the access key is about the framework itself, so it stops being counted as a lab check (47 workflow checks instead of 48, 16 screen checks as before) and keeps running as one of the framework's own checks.  _(verified 2026-10-18: 47 + 16 green, secrecy self-check green outside the count; before/after per Slices 2–7 parity)_
    - [x] When the engineer compares what each check does before and after this change, then every check sends the same requests to the lab or performs the same actions on the screens, and expects the same outcome.  _(verified 2026-10-18: same requests/actions: parity shown before every deletion (Slices 2–7))_
    - [x] When the lab cannot be reached, then the run still stops before the first check with the one existing message, and no scenario is reported as failed.  _(verified 2026-10-18: lab stopped: one LAB PRE-FLIGHT FAILED message per module, Tests run: 0, no scenario reported)_
    - [x] When each check prepares its data, then it still creates its own records with their own made-up names, and no scenario depends on another scenario having run.  _(verified 2026-10-18: UUID data per scenario; CheckIndependenceGuardTest green; green in random parallel order over 4 runs)_
    - [x] When a screen check hangs for 2 minutes, then the run's output names that check and says where it is stuck, and the run tries to stop it; when it cannot be stopped, then the whole run ends as failed within 10 minutes instead of hanging.  _(verified 2026-10-18: ScenarioWatchdogTest 4/4; live hang named at 2:00 in Slice 2; fork limit 600 s in ui-tests/pom.xml)_
    - [x] When the engineer picks a subset of checks by label (workflow, screen, smoke, regression), then the same checks are selected as before.  _(verified 2026-10-18: -Dgroups: regression|!(api|ui) 16/47, api 0/47, ui 16/0, smoke 3/3, unknown label 0/0 green)_
    - [x] When the engineer searches a report for the access key, the sign-in password or a patient's name in text, then none is found, as in spec 007.  _(verified 2026-10-18: secret scan of results, decoded report, logs, Surefire XML, feature files: 0 hits for key, password, TEST-patient- (scanner proven with planted values))_
    - [x] When a check fails in an automatic run, then the name shown in the run-page summary is a name the reader can find in that run's report by typing it into the report's search box.  _(verified 2026-10-18: summary name = report name, found by the report's search — 008-report-navigation-search-by-name.png)_
    - [x] When the engineer times the documented full local run before and after this change, then the run after it is at most 10% slower.  _(verified 2026-10-18: +0.4% (34.256 s vs 34.120 s, median of three, Slice 9))_

### 2.7. Documented

- **As a** reader new to the project, **I want** the README to tell me how the report is organised, **so that** I know where to look.
  - **Acceptance Criteria:**
    - [x] When the reader opens the README, then they find the list of topics in each family and one sentence on what each topic covers.  _(verified 2026-10-18: README "How the report is organised": topic table with one sentence per topic)_
    - [x] When the reader opens the README, then they find how to read an opened check: the short name, the description, and the given, when and then steps.  _(verified 2026-10-18: README "Reading an opened check")_
    - [x] When the engineer opens the README, then they find where the scenario files are and how to add a new scenario to an existing topic.  _(verified 2026-10-18: README "Scenario files": paths, adding and removing a scenario)_
    - [x] When the reader opens the project's product notes, then they say that the project has a behaviour-driven layer, why the earlier decision to leave it out was reversed, and on what date.  _(verified 2026-10-18: product-definition.md and architecture.md: behaviour-driven layer, reversed on 2026-10-12 with the reason)_

---

## 3. Scope and Boundaries

### In-Scope

- Sorting the existing lab checks into named topics inside each family.
- A short name and a fuller description for every lab check, including checks that run once per row of data.
- A fixed, meaningful order of topics and checks in the report.
- Plain-language suite names.
- Writing every lab check (63) as a scenario in a plain-language scenario file, and showing the scenario's lines as the check's steps in the report.
- A README part on how the report is organised and on the scenario files.
- Updating the product notes to record that a behaviour-driven layer is now part of the project.

### Out-of-Scope

- Adding, removing or changing what any check tests: the scenarios describe the existing checks, one for one.
- Scenarios for the framework's own self-checks or for the lab's own tests.
- The framework's own self-checks; they stay out of the report.
- Changing the report tool, how the report is built, or how it is kept with an automatic run (spec 007).
- History and trends across runs.
- Publishing the report as a web page.
- Any change to the lab.

---

## Change Log

- 2026-10-12 — task planning, decided by the owner — the run-log secrecy check is about the framework's own log, not about the lab, and would not read as a plain-language scenario. It becomes one of the framework's self-checks: it leaves the report and the lab-check count (47 workflow checks instead of 48) and keeps running. §2.5 and §2.6 say so.
- 2026-10-12 — technical design (`technical-considerations.md` §2.9), decided by the owner — three criteria changed to what the report tool and the scenario runner can do. (1) §2.3: the report sorts groups by name only, so topics carry a two-digit number, and inside a sub-group the order is alphabetical instead of the order in the file. (2) §2.2 and §2.5: the report has three grouping levels, all in use, so each row of a per-row check is its own entry in the sub-group instead of being grouped under its check. (3) §2.6: the scenario runner has no time limit per check; a hung screen check is named and interrupted at 2 minutes, and if it cannot be stopped the whole run fails at 10 minutes. Before this change a hung check failed alone and the others went on.
- 2026-10-18 — Slice 10b, decided by the owner after the rendered check of Slice 10 — §2.2 changed from "at most 80 characters" to "at most 45 characters", so that no name is cut off with an ellipsis at 1440 px: the report's list pane is about 625 px wide, and a leaf's name gets about 320 px of it (the pane less the indentation of three group levels, the status and time column and a reserved box for example values). 80 characters never fit (33 of the 63 names were cut off). A row of a table of examples also no longer shows its example values in grey beside its name, and the report's set-up and tear-down entries carry plain names instead of hook method names (§2.1).
- 2026-10-18 — Slice 10c, decided by the owner during verification — the report and the run-page summaries call the two families "API tests" and "UI tests"; wherever the spec body says "workflow checks" and "screen checks" (including the epic and parentSuite labels in §2.1 and §2.2), it means these two families. The CI jobs keep their names, Workflow checks and Screen checks, because branch-protection rules select them by name. A description is shown in the report as paragraphs: the feature file's line breaks inside a paragraph become spaces, and a blank line stays a paragraph break (§2.2).
