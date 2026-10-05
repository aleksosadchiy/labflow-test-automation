# Lab Workflow SDET Automation Framework

A small, production-minded Java test automation framework built as a **reference project**. It simulates a simplified, regulated laboratory workflow — Patient → Order → Sample → Processing → Report — and demonstrates REST API automation, UI automation with Page Object Model, parallel test execution, test-data isolation, and CI/CD.

> This is a practice/portfolio project. It does not reproduce any real laboratory's architecture or business logic — see `context/project-brief.md` for the original brief.

## Status

🚧 In progress. Product definition, roadmap, and architecture are defined (see below); the mock lab application (the system under test) is built and runnable — see [Running the lab](#running-the-lab). The `api-tests` and `ui-tests` modules are scaffolded and green — see [Running Tests](#running-tests).

## Project Context

This project is managed with [AWOS](https://github.com/provectus/awos) (`.awos/`), which drives its planning docs under `context/product/`:

- **[Product Definition](context/product/product-definition.md)** — vision, audience, success metrics, scope.
- **[Roadmap](context/product/roadmap.md)** — phased build plan, starting with Phase 0 (standing up the mock lab backend + a tiny front-end as the system under test).
- **[Architecture](context/product/architecture.md)** — stack decisions and rationale.
- **[Specialist Agents Coverage](context/product/hired-agents.md)** — which Claude Code subagents are configured for this stack.

## Planned Tech Stack

| Layer | Technology |
| --- | --- |
| Language / Build | Java 17, Maven (multi-module) |
| Test Framework | JUnit 5 (parameterized tests, tags, parallel execution) |
| API Automation | RestAssured |
| UI Automation | Playwright for Java, Page Object Model |
| Mock System Under Test | Spring Boot + Thymeleaf (in-memory data) |
| CI/CD | GitHub Actions (separate API/UI jobs) |
| Logging / Reporting | SLF4J + Logback, Surefire/JUnit XML |

Full rationale and alternatives considered are in [`context/product/architecture.md`](context/product/architecture.md).

## Planned Structure

```text
.
├── mock-lab-app/     # Spring Boot + Thymeleaf system under test (Phase 0)
├── common/           # Shared domain models and utilities
├── api-client/       # REST API client layer (RestAssured)
├── api-tests/        # Positive/negative API test suites
├── ui-tests/         # Page Objects in src/main, screen checks in src/test
├── test-data/        # Test-data builders/factories
└── .github/workflows/tests.yml
```

## Running the lab

The mock lab (`mock-lab-app/`) is the system under test. It needs only a JDK 17+ — Maven is provided by the bundled Maven Wrapper (`mvnw`, Maven 3.9.11), and no Docker or database is involved (data is in-memory).

**Start it locally (one command):**

```bash
cd mock-lab-app && ./mvnw spring-boot:run
```

**Or build and run the jar** (the form intended for CI, where it is started in the background with `&`):

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

# only the lab-workflow checks, plus the framework's quick self-checks (they need no lab)
./mvnw -pl api-tests -am test
```

`-pl api-tests` selects the checks module, and `-am` ("also make") builds the modules it depends on (`common`, `api-client`) in the same run; without it Maven cannot find them on a fresh clone, because nothing is installed into the local repository.

**Picking what to run.** Every check carries labels: `api` (a lab-workflow check) or `ui` (a screen check), plus `regression`, and a few also carry `smoke`. Pick a subset with Surefire's `-Dgroups=<label expression>`. Quote the expression, because the shell would otherwise read `|` and `!` itself:

| Subset | Command |
| --- | --- |
| smoke (the key checks plus the framework's self-checks) | `./mvnw test -Dgroups="smoke \| !(api \| ui)"` |
| regression (every lab check) | `./mvnw test -Dgroups=regression` |
| workflow checks only | `./mvnw -pl api-tests -am test -Dgroups=api` |
| screen checks only | `./mvnw -pl ui-tests -am test -Dgroups=ui` |

In the smoke command, `!(api | ui)` means "carries neither `api` nor `ui`": those are the framework's own quick self-checks (unit and guard tests), which are left unlabelled on purpose and need no lab. The smoke label is on six checks — three workflow checks (the whole journey from patient to report; a registered sample starts as Accessioned; a request without the key is refused) and three screen checks (a valid sign-in opens the sample list; a registered sample is listed as Accessioned; the full journey through the screens ends in the report).

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

**Screen checks.** `ui-tests` drives the lab through a real browser (Chromium by default, or WebKit with `-Dbrowser=webkit`) with Playwright. It needs the same running lab as the checks above, plus a one-time browser install.

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
# only the screen checks, plus the framework's quick self-checks (common, api-client)
./mvnw -pl ui-tests -am test

# everything: API checks and screen checks together
./mvnw clean test
```

`./mvnw clean test` now includes `ui-tests` in the reactor. If the API checks fail, add `-fae` ("fail at end") so the build still runs `ui-tests` and reports both verdicts in one run, instead of stopping at the first red module:

```bash
./mvnw clean test -fae
```

**Watching a run.** Screen checks run headless by default:

```bash
./mvnw -pl ui-tests -am test -Dheaded=true -DslowMo=250
```

`-Dheaded=true` shows the Chromium window instead of running it headless; `-DslowMo=<ms>` slows every action down so it is easier to follow.

**Investigating a failure.** A failing check saves a screenshot and a Playwright trace, in a folder named after the browser (for example `chromium`) and its scenario; a passing check keeps nothing:

```text
ui-tests/target/ui-failures/<browser>/<TestClass>/<scenario>/screenshot.png
ui-tests/target/ui-failures/<browser>/<TestClass>/<scenario>/trace.zip
```

Both live under `target/`, so `./mvnw clean` removes them. Open a saved trace with the same plugin used for the browser install, or by dragging the file onto [trace.playwright.dev](https://trace.playwright.dev), which runs entirely in the browser:

```bash
./mvnw -N exec:java -Dexec.args="show-trace ui-tests/target/ui-failures/<browser>/<TestClass>/<scenario>/trace.zip"
```

Sign-in is kept out of every trace on purpose: recording pauses while the real password is typed and submitted, so the lab's password never lands in a screenshot, a trace, or any other output.

## Running checks side by side

Checks run several at a time, for both the lab-workflow checks and the screen checks, so the suite stays fast as it grows. By default as many run at once as the machine has processor cores; `-DchecksAtOnce=N` caps that, and `-DchecksAtOnce=1` runs them one after another, which is the way to investigate a confusing failure without other checks running alongside. It is safe because every check makes its own `TEST-…` data and never looks at anyone else's. Each worker thread gets its own Playwright and browser (Playwright for Java is not thread-safe per instance), and every screen check gets its own fresh browser window inside it.

Measured on one running lab, Apple M4 Pro (14 cores), JDK 26, Maven 3.9.11: one warm-up run, then three timed runs of each, taking Maven's `Total time:` line and its median:

| Command | Runs (s) | Median |
| --- | --- | --- |
| `./mvnw test -Dgroups=regression` (side by side, default) | 6.222 / 6.470 / 6.555 | **6.470 s** |
| `./mvnw test -Dgroups=regression -DchecksAtOnce=1` (one at a time) | 8.985 / 9.067 / 9.107 | **9.067 s** |
| `./mvnw test -Dgroups="smoke \| !(api \| ui)"` (for information only) | — | 8.558 s |

Side by side is about 1.40× faster than one at a time. Most of the gain is in the screen checks (`ui-tests` took 3.9 s instead of 6.2 s). Smoke is slower than regression here because it also runs the framework's roughly 380 self-checks, and at this size Maven's own start-up dominates both, so the two are not compared.

## Run log

Every run starts with one settings line, with the key hidden: `Lab run: environment=… address=… key=*** browser=…` (the `browser=…` part appears only in `ui-tests`). After that, every request a check makes to the lab is one line with the check's name, the method, the path, the status, how long it took and the identifiers involved (patient, order, sample), for example `POST /api/samples -> 201 in 34 ms [patient=… order=… sample=…]`.

The console shows these lines at INFO. The full detail goes to one file per module, rewritten on every run and removed by `./mvnw clean`:

```text
api-tests/target/logs/api-tests.log
ui-tests/target/logs/ui-tests.log
```

Never logged, even with `-DlogBodies=true`: headers, the access key, the sign-in password and patient names (bodies, when switched on, have `name` and `password` values replaced by `***`). To investigate a failing check, search the log for its name exactly as the failure prints it, `ClassName › display name`, which finds every line that check wrote:

```bash
grep "<ClassName> › <display name>" api-tests/target/logs/api-tests.log
```
