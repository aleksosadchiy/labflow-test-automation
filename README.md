# LabFlow Test Automation

[![CI](https://github.com/aleksosadchiy/labflow-test-automation/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/aleksosadchiy/labflow-test-automation/actions/workflows/ci.yml?query=branch%3Amain)

_The repository is private, so the CI badge renders only for people with access to it._

A small, production-minded Java test automation framework. It simulates a simplified, regulated laboratory workflow — Patient → Order → Sample → Processing → Report — and demonstrates REST API automation, UI automation with Page Object Model, parallel test execution, test-data isolation, and CI/CD.

> This is a practice and reference project. It does not reproduce any real laboratory system's architecture or business logic — see [`context/project-brief.md`](context/project-brief.md) for the project brief.

## Status

✅ Built. Every roadmap item is done, and specs 001–007 under `context/spec/` are verified against their acceptance criteria. Spec 008 (every lab check written as a plain-language scenario, and a report sorted into topics) is not verified yet. The mock lab application (the system under test) is runnable — see [Running the lab](#running-the-lab). The `api-tests` and `ui-tests` modules run against it — see [Running Tests](#running-tests) — and GitHub Actions runs them on every push — see [Continuous integration](#continuous-integration).

## Project Context

This project is managed with [AWOS](https://github.com/provectus/awos) (`.awos/`), which drives its planning docs under `context/product/`:

- **[Product Definition](context/product/product-definition.md)** — vision, audience, success metrics, scope.
- **[Roadmap](context/product/roadmap.md)** — the phased build plan, from Phase 0 (the mock lab backend + a tiny front-end as the system under test) to the CI pipeline; all of it is done.
- **[Architecture](context/product/architecture.md)** — stack decisions and rationale.
- **[Specialist Agents Coverage](context/product/hired-agents.md)** — which Claude Code subagents are configured for this stack.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language / Build | Java 17, Maven (multi-module) |
| Test Framework | JUnit 5 Platform (tags, parallel execution); JUnit Jupiter for the framework's own self-checks |
| Scenarios | Cucumber 7: every lab check is a Given / When / Then scenario in a plain-text feature file |
| API Automation | RestAssured |
| UI Automation | Playwright for Java, Page Object Model |
| Mock System Under Test | Spring Boot + Thymeleaf (in-memory data) |
| CI/CD | GitHub Actions: one workflow, a Build job, then separate API tests and UI tests jobs in parallel, then a Report job |
| Logging / Reporting | SLF4J + Logback, Surefire/JUnit XML, Allure Report (one visual page per run) |

Full rationale and alternatives considered are in [`context/product/architecture.md`](context/product/architecture.md).

## Structure

```text
.
├── mock-lab-app/     # Spring Boot + Thymeleaf system under test (a separate Maven build)
├── common/           # Shared domain models and utilities
├── api-client/       # REST API client layer (RestAssured)
├── api-tests/        # The workflow checks: scenarios in src/test/features, their code in src/test/java
├── ui-tests/         # Page Objects in src/main; the screen checks as scenarios in src/test
├── test-data/        # Test-data builders/factories
└── .github/
    ├── workflows/ci.yml  # the CI pipeline
    └── scripts/          # lab.sh (start/stop the lab), access_key.sh (the CI access key), junit_summary.py (job summary), report.sh (build the visual report)
```

Each module has its own short `README.md` saying what it holds and how to run its tests.

The two check modules share one layout (`api-tests` shown; in `ui-tests` the package is `com/labflow/ui/tests` and the runner is `ScreenScenariosTest`):

```text
api-tests/src/test/
├── features/                    # the scenario files, one per topic: 01-getting-access.feature …
├── java/com/labflow/tests/
│   ├── WorkflowScenariosTest    # the runner: runs the scenario files, so Maven counts every scenario
│   ├── steps/                   # the code behind the scenario lines, one class per scenario file
│   ├── hooks/                   # runs before and after every scenario (log name, report groups; in ui-tests also the browser, the time limit and failure evidence)
│   ├── world/                   # LabWorld / UiWorld: what one scenario knows, shared by its step classes
│   ├── framework/               # the machinery: the lab pre-flight, the report steps (ui-tests: browsers, evidence, time limit)
│   └── selfcheck/               # the framework's own checks: guards/ (the rules) and framework/ (tests of the machinery)
└── resources/                   # settings only: junit-platform.properties, logback-test.xml, allure.properties
```

## Running the lab

The mock lab (`mock-lab-app/`) is the system under test. It needs only a JDK 17+ — Maven is provided by the bundled Maven Wrapper (`mvnw`, Maven 3.10.0), and no Docker or database is involved (data is in-memory).

**Start it locally (one command):**

```bash
cd mock-lab-app && ./mvnw spring-boot:run
```

**Or build and run the jar** (the form CI uses: `.github/scripts/lab.sh` starts it in the background and waits for `/health`):

```bash
cd mock-lab-app
./mvnw -q package -DskipTests
java -jar target/mock-lab-app.jar
```

The lab listens on `http://localhost:8080` by default (`server.port` in `application.yml`).

**Readiness check.** `GET /health` is open (no login needed) and returns `503 {"status":"STARTING"}` until start-up, including seeding the fixture data, has finished, then `200 {"status":"UP"}`. Wait for it with a curl retry poll (`--retry-connrefused` also retries while the port is still closed):

```bash
curl -sf --retry 30 --retry-delay 1 --retry-max-time 30 --retry-connrefused http://localhost:8080/health
```

Do not use `/login` as a readiness signal: the port opens before the seed data is loaded, so `/login` can respond while the lab is still starting.

**Seeded fixtures.** Every start begins from the same clean state (nothing is kept between runs), so tests can rely on these values (defined in `mock-lab-app/src/main/java/com/mocklab/config/DataSeeder.java` and `application.yml`):

| Patient ID | Name |
| --- | --- |
| `PAT-1001` | Alice Johnson |
| `PAT-1002` | Brian Smith |
| `PAT-1003` | Carla Diaz |
| `PAT-1004` | David Chen |
| `PAT-1005` | Emily Turner |
| `PAT-1006` | Farid Haidari |
| `PAT-1007` | Grace Kim |

The one seeded login user is `LabTech`, with the local mock-only password `I_Like_This_Lab`. Both are defaults in `application.yml` and can be overridden with the `MOCK_LAB_USERNAME` and `MOCK_LAB_PASSWORD` environment variables.

## Running Tests

The checks run against a lab you start yourself: they never build, start or stop it. Use two terminals.

**Required tools.** A Java 17+ runtime and internet access the first time (the Maven Wrapper `./mvnw` downloads Maven, and Maven downloads the dependencies). Nothing else needs to be installed.

**Terminal 1 — start the lab** and leave it running (see [Running the lab](#running-the-lab)). Wait until `http://localhost:8080/health` answers `200`:

```bash
cd mock-lab-app && ./mvnw spring-boot:run
```

**Terminal 2 — run the checks**, from the repository root:

```bash
# everything (the framework's self-checks and the lab-workflow checks); the overall result is shown at the end
./mvnw clean test

# only the lab-workflow checks, plus the framework's self-checks of that module
./mvnw -pl api-tests -am test
```

`-pl api-tests` selects the checks module, and `-am` ("also make") builds the modules it depends on (`common`, `test-data`, `api-client`) in the same run; without it Maven cannot find them on a fresh clone, because nothing is installed into the local repository.

**Picking what to run.** Every lab check carries labels, written as tags in its scenario file (see [Scenario files](#scenario-files)): `api` (a lab-workflow check) or `ui` (a screen check), plus `regression`, and a few also carry `smoke`. Pick a subset with Surefire's `-Dgroups=<label expression>`. Quote the expression, because the shell would otherwise read `|` and `!` itself:

| Subset | Command |
| --- | --- |
| smoke (the key checks plus the framework's self-checks) | `./mvnw test -Dgroups="smoke \| !(api \| ui)"` |
| regression (every lab check) | `./mvnw test -Dgroups=regression` |
| workflow checks only | `./mvnw -pl api-tests -am test -Dgroups=api` |
| screen checks only | `./mvnw -pl ui-tests -am test -Dgroups=ui` |

In the smoke command, `!(api | ui)` means "carries neither `api` nor `ui`": those are the framework's own self-checks (unit and guard tests), which are left unlabelled on purpose. They need no lab, except the run-log secrecy self-check, which drives the lab to have something to find in the log. The smoke label is on six checks — three workflow checks (the whole journey from patient to report; a registered sample starts as Accessioned; a request without the key is refused) and three screen checks (a valid sign-in opens the sample list; a registered sample is listed as Accessioned; the full journey through the screens ends in the report).

**A label that matches nothing** is not an error: the run finishes green and prints `Lab checks run: 0 (labels: …)`, so you can see that nothing ran (for example `-Dgroups=nosuch`). The lab still has to be running, because the pre-flight check (below) runs before any label is applied.

**Settings** come from outside the project — no file is edited:

| Setting | What it sets | Default |
| --- | --- | --- |
| `BASE_URL` (environment) or `-DbaseUrl=…` (Maven property; wins if both are set) | The lab's address | `http://localhost:8080` |
| `API_TOKEN` (environment only) | The lab's access key | The lab's documented local key, `mock-lab-local-token` |
| `MOCK_LAB_USERNAME` (environment only) | The screen checks' sign-in username | `LabTech` |
| `MOCK_LAB_PASSWORD` (environment only, same as `API_TOKEN`) | The screen checks' sign-in password | The lab's documented local password, `I_Like_This_Lab` |
| `ENVIRONMENT` (environment) or `-Denvironment=…` (wins if both are set) | The environment's name: `local` or `ci`. It supplies only the default lab address (both are `http://localhost:8080`); an explicit `BASE_URL` / `-DbaseUrl` always wins. An unknown value stops the run before any check, with `Unknown environment 'x'. Known: local, ci` | `local` |
| `BROWSER` (environment) or `-Dbrowser=…` (wins if both are set) | The browser for the screen checks: `chromium` or `webkit`. Codespaces and dev containers may set `BROWSER` to a helper program's path; that is rejected as unsupported, so override it with `-Dbrowser=chromium` | `chromium` |
| `-DchecksAtOnce=N` (Maven property only) | How many checks run at the same time. `1` runs them one at a time. Lower it on small machines and CI runners (for example `-DchecksAtOnce=2`): every screen-check worker has its own browser, which costs memory | The number of processor cores |
| `LOG_BODIES` (environment) or `-DlogBodies=true` (wins if both are set) | Adds the request and answer bodies to the log file, with names and the password hidden (see [Run log](#run-log)) | `false` |
| `-Dheaded=true` (Maven property only) | Shows the browser instead of running headless | `false` |
| `-DslowMo=<ms>` (Maven property only) | Slows every Playwright action down by this many milliseconds | `0` |

```bash
./mvnw clean test -DbaseUrl=http://localhost:18080
API_TOKEN=<key> ./mvnw clean test
```

**When the lab cannot be reached, or rejects the key,** the run stops before any lab-workflow check with one message: the first `[ERROR]` line after the `T E S T S` line of the module that hit it first (`api-tests` or `ui-tests` — both check independently), starting `LAB PRE-FLIGHT FAILED:`. It names the address that was tried and how to start the lab, or says the access key was rejected. Zero checks run in that module and the build fails.

**Caution — keep the key out of reports and logs.** A key passed as `-DapiToken=…` is ignored by the framework, but Surefire still records command-line system properties in its XML reports (`target/surefire-reports`), and `./mvnw -X` prints every environment variable, including `API_TOKEN`. Never share or attach such reports or debug logs (for example to CI). Use `-DbaseUrl` only for a non-secret address.

**UI tests.** `ui-tests` drives the lab through a real browser (Chromium by default, or WebKit with `-Dbrowser=webkit`) with Playwright. It needs the same running lab as the checks above, plus a one-time browser install.

**One-time browser install.** Playwright's browsers are not downloaded automatically before a run — the first test would otherwise silently pull in every browser (about 1 GB) and can time out doing it. Install Chromium once, from the repository root:

```bash
./mvnw -N exec:java
```

This runs Playwright's own CLI (`install chromium`) as a Maven plugin dependency, not a project dependency, so it needs no reactor build and no Node.js toolchain. It is safe to re-run: it reports Chromium as already installed. On Linux CI, ask for the OS libraries Chromium needs too:

```bash
./mvnw -N exec:java -Dexec.args="install --with-deps chromium"
```

**WebKit** needs its own one-time install, from the repository root, before `-Dbrowser=webkit` works (on Linux CI add `--with-deps`, as above):

```bash
./mvnw -N exec:java -Dexec.args="install webkit"
```

Firefox is not supported: Playwright's Firefox does not start on macOS 27, the development machine, even outside the framework, so it could not be proven here.

**Running the screen checks.**

```bash
# only the screen checks, plus the framework's quick self-checks (common, test-data, api-client)
./mvnw -pl ui-tests -am test

# everything: API checks and screen checks together
./mvnw clean test
```

`./mvnw clean test` runs `ui-tests` first and `api-tests` after it (the order of the modules in the root `pom.xml`). If the screen checks fail, Maven stops there and skips `api-tests`. Add `-fae` ("fail at end") so the build still runs it and reports both verdicts in one run:

```bash
./mvnw clean test -fae
```

**Watching a run.** The screen checks run headless by default:

```bash
./mvnw -pl ui-tests -am test -Dheaded=true -DslowMo=250
```

`-Dheaded=true` shows the Chromium window instead of running it headless; `-DslowMo=<ms>` slows every action down so it is easier to follow.

**Investigating a failure.** A failing screen check saves a screenshot and a Playwright trace, in a folder named after the browser (for example `chromium`), its topic and its scenario; a passing check keeps nothing:

```text
ui-tests/target/ui-failures/<browser>/<topic>/<scenario>-<line>/screenshot.png
ui-tests/target/ui-failures/<browser>/<topic>/<scenario>-<line>/trace.zip
```

The topic and the scenario's short name are written in lower case with dashes, and `<line>` is the scenario's line in its scenario file, which keeps the rows of one table of examples apart. For example, "A wrong password is refused and the Sign In screen stays" (line 16 of `ui-tests/src/test/features/01-getting-access.feature`) fails into `ui-tests/target/ui-failures/chromium/01-getting-access/a-wrong-password-is-refused-and-the-sign-in-screen-stays-16/`.

Both live under `target/`, so `./mvnw clean` removes them. Open a saved trace with the same plugin used for the browser install, or by dragging the file onto [trace.playwright.dev](https://trace.playwright.dev), which runs entirely in the browser:

```bash
./mvnw -N exec:java -Dexec.args="show-trace ui-tests/target/ui-failures/<browser>/<topic>/<scenario>-<line>/trace.zip"
```

Sign-in is kept out of every trace on purpose: recording pauses while the real password is typed and submitted, so the lab's password never lands in a screenshot, a trace, or any other output.

The same screenshot and trace are also inside the failed check's entry in the [visual report](#visual-report).

**A screen check that hangs.** Every screen scenario has 2 minutes. When that time is up, the run log names the scenario (`Scenario '<topic> › <short name>' ran longer than PT2M; interrupting its thread`), adds a dump of every thread that shows where it is stuck, and interrupts the scenario. A known limitation: an interrupt only frees a call that listens for it. Some Playwright calls do not, and then nothing can stop that one scenario on its own. The whole `ui-tests` run is then ended as failed by Surefire's fork limit after 10 minutes (`surefire.timeout` in `ui-tests/pom.xml`, "There was a timeout in the fork"), instead of hanging. Before spec 008 a stuck check failed alone and the others went on; the scenario runner (Cucumber) has no time limit per scenario, so that is no longer possible.

## Running checks side by side

Checks run several at a time, for both the lab-workflow checks and the screen checks, so the suite stays fast as it grows. By default as many run at once as the machine has processor cores; `-DchecksAtOnce=N` caps that, and `-DchecksAtOnce=1` runs them one after another, which is the way to investigate a confusing failure without other checks running alongside. It is safe because every check makes its own `TEST-…` data and never looks at anyone else's. Each worker thread gets its own Playwright and browser (Playwright for Java is not thread-safe per instance), and every screen check gets its own fresh browser window inside it.

Measured on 2026-10-12 on one running lab, Apple M4 Pro (14 cores), JDK 26, Maven 3.10.0: one warm-up run, then three timed runs of each, taking Maven's `Total time:` line and its median:

| Command | Runs (s) | Median |
| --- | --- | --- |
| `./mvnw test -Dgroups=regression` (side by side, default) | 34.275 / 34.003 / 34.204 | **34.204 s** |
| `./mvnw test -Dgroups=regression -DchecksAtOnce=1` (one at a time) | 42.191 / 42.049 / 42.138 | **42.138 s** |
| `./mvnw test -Dgroups="smoke \| !(api \| ui)"` (for information only) | — | 37.297 s |

Side by side is about 1.23× faster than one at a time. Almost all of the gain is in the screen checks (`ui-tests` took 9.9 s instead of 17.0 s). Smoke is slower than regression here because it also runs the framework's self-checks, so the two are not compared.

About half of every run is coverage recording: the same regression run with `-Djacoco.skip=true` took 17.4 s (one run). These figures replace an earlier table, measured before coverage, the visual report and the dependency updates were added, which showed 6.5 s and 9.1 s; what accounts for the rest of the difference has not been measured.

## Run log

Every run starts with one settings line, with the key hidden: `Lab run: environment=… address=… key=*** browser=…` (the `browser=…` part appears only in `ui-tests`). After that, every request a check makes to the lab is one line with the check's topic and short name, the method, the path, the status, how long it took and the identifiers involved (patient, order, sample), for example:

```text
04. Registering a sample › A new sample starts at Accessioned POST /api/samples -> 201 in 4 ms [patient=… order=… sample=…]
```

The console shows these lines at INFO. The full detail goes to one file per module, rewritten on every run and removed by `./mvnw clean`:

```text
api-tests/target/logs/api-tests.log
ui-tests/target/logs/ui-tests.log
```

Never logged, even with `-DlogBodies=true`: headers, the access key, the sign-in password and patient names (bodies, when switched on, have `name` and `password` values replaced by `***`). To investigate a failing check, take its short name from the failure (Maven prints it as `WorkflowScenariosTest.<short name>` or `ScreenScenariosTest.<short name>`) and search the log for its topic and short name, which finds every line that check wrote (`-F` reads the text literally, dots included):

```bash
grep -F "<topic> › <short name>" api-tests/target/logs/api-tests.log
grep -F "04. Registering a sample › A new sample starts at Accessioned" api-tests/target/logs/api-tests.log
```

## Visual report

[Allure Report](https://allurereport.org/) is a widely used open-source tool that turns test results into a browsable HTML page. It is here so that one run can be read, and handed to a reviewer, without opening logs. While the checks run, each lab check writes one small result file into `target/allure-results` at the repository root. Nothing more happens until you ask for the report.

**Build it** with one command, from the repository root, after the checks have run:

```bash
.github/scripts/report.sh
```

The last line it prints says where the report is and how many checks it covers. The report is this one self-contained file; the other files the plugin leaves in the same folder are not needed to open it or to share it:

```text
target/site/allure-maven-plugin/index.html
```

Open it by double-clicking. It is self-contained, so it needs no server and no network. The first build downloads the report tool into `.allure/` (ignored by git), so that one build needs internet access.

**What it shows that the run-page summary does not.** The summary gives totals and the names of failing checks; the report shows every lab check under its short name with its verdict, duration and labels (`api` or `ui`, `smoke`, `regression`, `accepted` or `refused`), sorted into families and topics (see [How the report is organised](#how-the-report-is-organised)). A workflow check lists the requests it made to the lab, in order, as the same lines the [run log](#run-log) holds. A failed screen check carries its picture and its recording (the Playwright trace, downloadable from the report and opened as described under [Running Tests](#running-tests)).

**What it lists.** Only the checks that test the lab. The framework's own self-checks never appear; they keep their console and summary output.

**What it never contains.** The access key, the sign-in password, and request or answer content are never in the report; a patient name appears only as a made-up `TEST-` name, in a failure message or in a failed screen check's picture and recording.

**One report covers one run.** Every Maven build that includes the root project (`test`, `test-compile`, even `validate`) first empties the collected results and removes the previous report. That is every command under [Running Tests](#running-tests), because each builds everything or uses `-am`; a `-pl` selection without `-am` leaves the root project out and empties nothing. So build the report straight after the checks, before any other such build. With no results the script says so and builds nothing.

| Run | The report holds |
| --- | --- |
| `./mvnw clean test` | both families |
| `./mvnw -pl api-tests -am test` | the workflow checks only |
| `./mvnw -pl ui-tests -am test` | the screen checks only |

**Checking the report's content.** `Spec008ReportContentAcceptanceTest` reads the result files of a run and checks what a reader meets in the report: the family / topic / sub-group tree, the short names and descriptions, the steps word for word, the request lines, plain set-up names, the names matching Surefire's and the run log's, and no secret. It needs the results of one complete run (63 entries), so it runs after the run, not during it (during `./mvnw clean test` it skips itself). Locally, with a running lab:

```bash
./mvnw clean test
.github/scripts/report.sh
./mvnw -B -pl common,test-data,api-client test -Dtest=Spec008ReportContentAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false
```

The last command must not include `clean` or `-am`. Either one brings the root project into the build, and that empties `target/allure-results` before the test reads it. CI runs the same check in the Report job (see [Continuous integration](#continuous-integration)).

**Why a script and not a Maven command.** Built through the report plugin's Maven goal alone, Allure Report loads a Google Analytics tag into the page and reports each build to Google. The script switches both off (it sets `ALLURE_NO_ANALYTICS=true`), and fails if the built page still holds the tag.

## How the report is organised

Open the report's **Behaviors** view. It has three levels:

1. **Two families:** "API tests" (the workflow checks of the `api-tests` module, which drive the lab through its API) and "UI tests" (the screen checks of the `ui-tests` module, which drive it through its screens). The rest of this README says "workflow checks" and "screen checks" for the same two families. The CI jobs carry the same two names (they were called Workflow checks and Screen checks until 2026-10-18), and so do their summaries on the run page ("API tests", and "UI tests" with the browser in brackets).
2. **Topics** inside each family, numbered 01 to 09 in the order of the lab's workflow: access first, then patient, order, sample, the stages, result and report. A number means the same topic in both families, so "04. Registering a sample" is the same subject under both. A family lists only the topics it covers. Each topic shows how many of its checks passed and failed without being opened. The numbers are there because the report tool sorts groups by name only.
3. **Two sub-groups** inside each topic: "Accepted" (the lab does what was asked) before "Refused" (the lab turns the request away). A sub-group with no check is not shown. Inside a sub-group the checks are listed alphabetically by short name.

The checks run side by side and in a random order, but the report lists them in this same order after every run, so two reports can be compared. The **Suites** view has the same three levels under the same names. The report holds 63 entries: 47 API tests and 16 UI tests. A scenario with a table of examples gives one entry per row, each under its own short name.

**The topics.** One sentence each, from the description at the top of the topic's scenario file:

| Topic | API tests | UI tests |
| --- | --- | --- |
| 01. Getting access | The lab serves only callers who present its access key; a missing or wrong key is turned away and nothing changes. | Only signed-in people see the screens: valid details are let in, anything else is turned away, and a signed-out visitor is sent back to Sign In. |
| 02. Patients | A patient is registered under a name and can be looked up by its id; a request without a usable name is refused. | — |
| 03. Orders | An order is placed for a known patient and can be looked up; an order for no patient, or for one the lab has never seen, is refused. | — |
| 04. Registering a sample | Every new sample starts at Accessioned; a request that is missing something, names something unknown or repeats a sample type within one order is refused. | A sample registered on the Create Sample screen appears in the Sample List as Accessioned; the screen refuses what it cannot register and says why. |
| 05. Moving through the stages | A sample moves forward one stage at a time to Report Generated; skipping ahead, going back, repeating a stage or moving on from the last one is refused. | The Details screen shows where a sample stands among the eight stages and moves it on with Advance; at the last stage it offers the report. |
| 06. Results and reports | The result is available from Result Ready on and the report from Report Generated on; asked for earlier, the lab says it is not ready yet. | The Report screen shows a sample's report from Report Generated on; earlier, it says the report is not ready yet. |
| 07. Unknown samples | Whatever is asked about a sample the lab has never heard of, it answers that the sample was not found, and nothing changes. | The Details screen of a sample the lab has never heard of says the sample was not found. |
| 08. Malformed and unsupported requests | A request whose content the lab cannot read, or an action it does not offer on a sample, is refused, and nothing is created or removed. | — |
| 09. The whole journey | A patient, an order and a sample go from registration to the finished report, and every answer belongs to that sample. | A sample registered on screen is taken through every stage to its finished report, and every screen shows that sample. |

**Reading an opened check.**

- **The title** is the check's short name, the title of its scenario (at most 45 characters, so it fits the report's list pane at 1440 pixels without being cut off with an ellipsis; unique in its topic). For a row of a table of examples, the row's own words are filled into the title, and the row's values are not shown beside it.
- **The description** under it says what the check is about and why: the topic's sentences, then the scenario's own explanation, both as written in the scenario file.
- **The steps** are the scenario's lines, each starting with Given (what was set up), When (what was done), Then (what was expected) or And, word for word and in the same order.
- **In a workflow check**, each request to the lab sits under the line that made it, in the same form as in the [run log](#run-log).
- **When a check fails**, the line that failed is marked failed with the reason, the lines before it as passed and the lines after it as not run.
- **A failed screen check** also carries its picture and its recording (the screenshot and the Playwright trace), in its tear-down entry after the steps.
- The set-up and tear-down entries around the steps are the framework's hooks, and each carries a plain name (for example "Opening a fresh browser page" or "Closing the page, keeping a picture and recording on failure"). They are not part of the scenario.

## Scenario files

Every lab check is a scenario: a short story of Given, When and Then in a plain-text scenario file (Cucumber's "feature file", written in its Gherkin language). There is one file per topic and family:

```text
api-tests/src/test/features/   # 01-getting-access.feature … 09-the-whole-journey.feature (nine topics)
ui-tests/src/test/features/    # topics 01, 04, 05, 06, 07 and 09
```

A file starts with its family's tags (`@api @regression` or `@ui @regression`), then `Feature:` with the topic's name (`Feature: 04. Registering a sample`) and one or two sentences on what the topic covers. Maven copies the files onto the test classpath under `features/`, where the runner (`WorkflowScenariosTest` or `ScreenScenariosTest`) finds them. The code that carries out each line is in the module's `steps/` folder, one class per scenario file and named after it: `RegisteringASampleSteps` for `04-registering-a-sample.feature`.

**Adding a scenario to an existing topic:**

1. Open the topic's file, for example `api-tests/src/test/features/04-registering-a-sample.feature`.
2. Add a `Scenario:` with a short name of at most 45 characters that no other scenario of the topic has, a sentence or two under it on what it checks and why (this becomes its description in the report), then its Given, When, Then and And lines.
3. Tag it with exactly one of `@accepted` (the lab does what is asked) or `@refused` (the lab turns it away), on the line above `Scenario:`. The family tag and `@regression` come from the `Feature:` line. Add `@smoke` only when that has been agreed: the smoke set is a fixed list of six, pinned by `LabelSelectionAcceptanceTest`.
4. Reuse existing lines word for word where you can. A line's code serves every scenario file of the module, so look in the other scenario files or in the module's `steps/` folder (`api-tests/src/test/java/com/labflow/tests/steps/`, `ui-tests/src/test/java/com/labflow/ui/tests/steps/`) first.
5. If you need a new line, add a method for it to the topic's `<Topic>Steps` class, with the line's exact text in a `@Given`, `@When` or `@Then` annotation. The class gets the scenario's state (`LabWorld` or `UiWorld`) through its constructor; the method takes its data from the `test-data` recipes and reaches the lab only through the `api-client` services or, for screens, the Page Objects. The guards fail on RestAssured or a locator in a step.
6. A scenario that runs once per row (`Scenario Outline` with `Examples`) keeps labels in its table, such as "with a wrong access key", never the access key, the password or another real value, because a feature file is read by everyone (the report does not show a row's values, but the file does). The step turns the label into the value.
7. Raise the agreed number of scenario entries by the number you added (each row of a table counts): `WORKFLOW_ENTRIES` in `api-tests/src/test/java/com/labflow/tests/selfcheck/guards/FeatureFilesGuardTest.java`, or `SCREEN_ENTRIES` in `ui-tests/src/test/java/com/labflow/ui/tests/selfcheck/guards/FeatureFilesGuardTest.java`.
8. Run the module against a running lab: `./mvnw -pl api-tests -am test` (or `-pl ui-tests`). Its guards check the new scenario: `DryRunGuardTest` fails on a line that no step method matches, naming the file and the line, and on a step method that no line uses; `FeatureFilesGuardTest` checks the count, the name's length and uniqueness, the topic numbers, and that no file holds the access key or the password; `LabelGuardTest` checks the tags. The new check then appears in the report under its topic and sub-group.

**Removing a scenario:** delete it from its file, lower `WORKFLOW_ENTRIES` or `SCREEN_ENTRIES` by the number of entries removed, and delete any step method that no other line uses (`DryRunGuardTest` names it). Then run the module as in step 8.

## Formatting, coverage and dependency updates

**Formatting.** [Spotless](https://github.com/diffplug/spotless) keeps a few mechanical rules: no unused imports, no trailing spaces, spaces instead of tabs, and a newline at the end of every file. The rules are deliberately light, so they match how the code is already written. CI's Build job fails on a violation. Run these from the repository root, and again inside `mock-lab-app/` for the lab (it is a separate Maven build):

```bash
./mvnw spotless:check   # report violations
./mvnw spotless:apply   # fix them
```

**Coverage.** [JaCoCo](https://www.jacoco.org/jacoco/) records which lines the tests execute. Every `./mvnw test` writes a report per module; open `index.html` in a browser:

```text
<module>/target/site/jacoco/index.html
mock-lab-app/target/site/jacoco/index.html
```

A module's report counts only that module's own classes. No minimum is enforced: the number is information, not a target.

**Secret scan.** CI's Build job runs [gitleaks](https://github.com/gitleaks/gitleaks) over the whole git history and fails if a commit holds something that looks like a key, token or password. Its settings are in [`.gitleaks.toml`](.gitleaks.toml). The framework's own tests need made-up secrets to prove that nothing leaks; each starts with `s3cret-`, the only pattern the scan lets through. Real values belong in the environment or in a local `.env` file, which git ignores.

**A guard for AI agents.** [`.claude/hooks/block_sensitive_files.py`](.claude/hooks/block_sensitive_files.py) is a Claude Code hook: a script the tool runs before each file or shell action. It refuses to read, copy or edit `.env` files and private keys, so a real secret cannot end up in an agent's conversation.

**Dependency updates.** [`.github/dependabot.yml`](.github/dependabot.yml) asks GitHub's Dependabot to open a weekly pull request with minor and patch updates for the framework, for the lab and for the GitHub Actions the workflow uses. Each pull request runs CI, so an update is merged only when the suite passes against it. Major versions are left out on purpose, because they are migrations to plan. Vulnerability alerts are a repository setting: Settings → Advanced Security → Dependabot alerts.

## Continuous integration

One GitHub Actions workflow, [`.github/workflows/ci.yml`](.github/workflows/ci.yml), named **CI**. It starts on every push to any branch, on every pull request into `main`, and by hand.

**Four jobs.** Build runs first. The two check jobs need it, so a broken build skips both and the run page shows Build as the failure. After Build they run in parallel, each on its own runner. Report runs last:

| Job | What it runs |
| --- | --- |
| **Build** | The secret scan (gitleaks), the formatting check (`./mvnw -B -ntp spotless:check`, for the framework and for the lab), then `./mvnw -B -ntp test-compile`, then the self-checks that need no lab: `./mvnw -B -ntp test -pl common,test-data,api-client`. It also builds the lab and fetches the Playwright CLI, so the Maven cache it saves already holds what the check jobs need |
| **API tests** | `./mvnw -B -ntp -pl api-tests -am test -Dgroups="regression \| !(api \| ui)"` |
| **UI tests** | Installs the browser with `./mvnw -B -ntp -N exec:java -Dexec.args="install --with-deps <browser>"` (the download is cached between runs), then `./mvnw -B -ntp -pl ui-tests -am test -Dgroups="regression \| !(api \| ui)"` |
| **Report** | Collects the result files both check jobs kept and builds the one [visual report](#visual-report) with `.github/scripts/report.sh`. It then checks the report's content with `./mvnw -B -ntp -pl common,test-data,api-client test -Dtest=Spec008ReportContentAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false` (see [Checking the report's content](#visual-report)). When both families delivered results, the step fails if that check fails or skips itself. It runs after both check jobs, also when one of them failed or was skipped by a hand-started run. It is skipped when Build failed. It cannot change the run's verdict |

Each check job starts its own lab with `.github/scripts/lab.sh start`: it builds the jar, starts it in the background and waits up to 60 s for `/health` to answer `200`. The job stops the lab at the end, whatever the result. The tag filter selects the regression set plus that module's own self-checks. Those self-checks run here rather than in Build because the pre-flight check needs a running lab. `-B -ntp` (batch mode, no download progress) keeps the log readable.

A typical run takes about 3–4 minutes: Build ~35 s, then API tests (~1 min) and UI tests (~1m45s) side by side, then Report (~30–40 s). Every job has a 15-minute limit.

**Starting a run by hand.** Go to Actions → CI → Run workflow, then pick `family` and `browser`. `family` is `both`, `api` or `ui` and chooses which check jobs run. `browser` is `chromium` or `webkit` and applies to the screen checks. Push and pull-request runs always run both families on Chromium. From the terminal, with the GitHub CLI:

```bash
gh workflow run CI -f family=ui -f browser=webkit
```

**Results.** Each job writes a short summary to the run's page, under the job graph: passed, failed, skipped, how long it took, and a table of failing checks by name. The same text is also in the log of the "Summarise results" step.

**Kept files** are on the run's page under *Artifacts*. GitHub keeps them for its default 90 days:

| Artifact | When | Contents |
| --- | --- | --- |
| `workflow-checks-results-<attempt>` | always | `api-tests` Surefire reports, the run log, the lab's start-up log, the Allure result files |
| `screen-checks-results-<browser>-<attempt>` | always | the same for `ui-tests` |
| `screen-failures-<browser>-<attempt>` | only when the UI tests job fails | a screenshot and a Playwright trace per failed check |
| `allure-report-<browser>-<attempt>` | whenever at least one check job kept result files | the visual report: one `index.html` (about 3 MB, about 1.5 MB as a download) |

`<attempt>` is the run attempt, so a re-run keeps its own copy.

**The run's report.** When the report was kept, the Report job's summary on the run's page has one line that says how many checks the report covers and links to it. The link leads to the `allure-report-…` file of that run and attempt and needs a signed-in GitHub user; clicking it downloads a zip, it does not open the report in the browser. Download `allure-report-…` (through that link or under *Artifacts*), unzip it and double-click `index.html`; it opens like a locally built one. It covers the families that ran: both on a push or pull request, one on a hand-started single-family run. Its name carries the selected browser even when only the workflow checks ran. After a re-run, the new attempt's report sits beside the first one and does not replace it. The report is kept only here, as a file; it is not published as a web page.

The Report job is allowed to fail without failing the run. A red Report job on a green run means one of two things. Either the report could not be built or kept, and the run page then carries an "Allure report missing" warning that gives the reason. Or the report was kept but its content check failed or skipped itself, and the "Check the report's content" step and the job's "Report content" summary name the reason. Either way, the checks themselves passed. When no check job kept any result files, the Report job stays green and shows the same warning.

**A failed screen check's trace.** To open a trace, download and unzip `screen-failures-…`. Then run this from the repository root, or drag the file onto [trace.playwright.dev](https://trace.playwright.dev):

```bash
./mvnw -N exec:java -Dexec.args="show-trace <path>/trace.zip"
```

Screen-failure evidence shows made-up `TEST-` patient names on purpose, because it shows the screen exactly as it was; a real regulated system would restrict access to such files or mask the patient column. The access key and the sign-in password never appear in any kept file.

**The access key.** An optional `API_TOKEN` repository secret (Settings → Secrets and variables → Actions) sets the key for both the lab and the checks. Without it, the workflow uses the lab's documented local key, read from the `${API_TOKEN:…}` default in `mock-lab-app/src/main/resources/application.yml`. The fallback is needed because an unset secret arrives as an empty string: Spring would accept the empty key, while the framework treats a blank key as unset and sends the default, so every check would get `401`. The key is masked before any step can print it.

**Blocking merges on a red run** is a repository setting and is not configured here. To turn it on, go to Settings → Branches and add a branch protection rule for `main`. Tick "Require status checks to pass before merging" and select `Build`, `API tests` and `UI tests`. Do not select `Report`: it is skipped when Build fails and is allowed to fail on its own, so requiring it would block a merge for a reason that has nothing to do with the lab.
