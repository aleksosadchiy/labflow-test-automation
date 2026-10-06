# Technical Specification: Automatic Check Runs on Every Change (GitHub Actions)

- **Functional Specification:** [functional-spec.md](./functional-spec.md)
- **Status:** In Review
- **Author(s):** Alex, with the `github-actions-cicd` specialist

---

## 1. High-Level Technical Approach

The pipeline is one GitHub Actions workflow, `.github/workflows/ci.yml`, with three jobs:

- **Build** compiles the whole reactor and runs the lab-free self-checks.
- **Workflow checks** and **Screen checks** both need Build. Each starts its own Mock Lab on its own runner VM, waits for `/health`, runs its module's regression set, writes a job summary and uploads its evidence.

Two small in-repo scripts keep the YAML readable and can be checked on a laptop:
- `.github/scripts/lab.sh` starts and stops the lab;
- `.github/scripts/junit_summary.py` turns Surefire XML into the job summary (Python 3 standard library only).

No new framework dependency, no third-party reporting action, no Docker. Framework and lab code are unchanged.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1. New Files

| Path | Responsibility |
|---|---|
| `.github/workflows/ci.yml` | Triggers, permissions, the three jobs, caches, artifacts, summaries |
| `.github/scripts/lab.sh` | `start`: build the lab jar, start it in the background (pid file and start-up log), poll `/health` for up to 60 s and fail early if the process dies. `stop`: kill by pid, escalate after 10 s, print `Lab stopped`. Plain bash + curl, so it also runs on macOS. |
| `.github/scripts/junit_summary.py` | Reads one module's `TEST-*.xml` and prints Markdown for `$GITHUB_STEP_SUMMARY`: passed/failed/skipped, the job's wall-clock duration, a one-line ✅ when green, a table of failing checks (plain-language test-case name, class, first line of the message) when red, and "No results" when no XML exists. |

### 2.2. Triggers and Permissions (FS §2.1)

- **Triggers:**
  - `push` to any branch;
  - `pull_request` with `branches: [main]`;
  - `workflow_dispatch` with two `choice` inputs:
    - `family`: `both` | `api` | `ui`, default `both`;
    - `browser`: `chromium` | `webkit`, default `chromium`.
- **Permissions:** top-level `permissions: contents: read`, which is least privilege. The summary goes through `$GITHUB_STEP_SUMMARY`, which needs no `checks: write`.
- **Concurrency:** no `concurrency` block, because the user decided that both runs finish. A branch with an open PR therefore runs twice per push (push + PR). This is an accepted cost in runner minutes only.
- **Runner:** `runs-on: ubuntu-24.04` (pinned rather than `ubuntu-latest`, for repeatability).

### 2.3. Job Graph (FS §2.2)

| Job id / name | needs | Runs when | Main command |
|---|---|---|---|
| `build` / "Build" | — | always | `./mvnw -B -ntp test-compile`, then `./mvnw -B -ntp test -pl common,test-data,api-client` |
| `api-tests` / "Workflow checks" | build | not a dispatch with `family == ui` | `./mvnw -B -ntp -pl api-tests -am test -Dgroups="regression \| !(api \| ui)"` |
| `ui-tests` / "Screen checks (<browser>)" | build | not a dispatch with `family == api` | `./mvnw -B -ntp -pl ui-tests -am test -Dgroups="regression \| !(api \| ui)"` |

- **Why Build runs only three modules' tests:** the `api-tests` and `ui-tests` pre-flight listeners run before any tag filter is applied, so their self-checks need a lab. They run inside the check jobs instead. The tag expression `regression | !(api | ui)` gives the lab checks plus that module's own self-checks (guards, acceptance tests).
- **A failed Build stops both check jobs** through `needs`, so the run page shows Build as the failed step.
- **Each check job rebuilds:** about 30–40 s with a warm Maven cache. *Rejected:* passing `target/` between jobs as artifacts, which is fragile and harder to explain.
- **Re-run failed jobs** comes free with GitHub's "Re-run failed jobs".

### 2.4. Lab Lifecycle in Each Check Job (FS §2.3)

1. **Start:** run `lab.sh start` (step "Start the Mock Lab").
   - It runs `cd mock-lab-app && ./mvnw -B -ntp -q package -DskipTests`.
   - It then starts `nohup java -jar target/mock-lab-app.jar > target/lab-startup.log 2>&1 &` and writes the pid to `target/lab.pid`.
   - It polls `curl -sf --max-time 2 http://localhost:8080/health` once a second, up to 60 times. It fails early if `kill -0 <pid>` fails.
   - On timeout it prints `::error title=Lab did not start::Mock Lab not ready on :8080 within 60 s` plus the last 50 log lines, and exits 1.
   - `/health` turns 200 only after seed data loads (spec 002), so there is no `sleep`.
2. **Checks:** the check step runs.
3. **Stop:** run `lab.sh stop` with `if: always()` (step "Stop the Mock Lab"). The runner VM is discarded anyway; the explicit stop makes "no longer running" visible in the log and is portable to self-hosted runners.
4. **Evidence:** `mock-lab-app/target/lab-startup.log` is part of the results artifact.

Each job has its own VM, so port 8080 never collides.

### 2.5. Settings and Secrets (FS §2.3, §2.4)

Job-level `env` on both check jobs; the same values reach the lab and Maven:

| Variable | Value | Why |
|---|---|---|
| `ENVIRONMENT` | `ci` | Spec 005 environment name; the banner shows it. |
| `BASE_URL` | `http://localhost:8080` | The lab started by this job. |
| `BROWSER` | `${{ inputs.browser \|\| 'chromium' }}` | Dispatch input; `chromium` on push and PR. |
| `API_TOKEN` | `${{ secrets.API_TOKEN \|\| 'mock-lab-local-token' }}` | The repository secret if set, else the lab's documented public default. |

- **Why the `||` fallback is required:** Spring's `${API_TOKEN:default}` only falls back when the variable is *missing*. The framework treats *blank* as unset. An unset secret expands to `""`, so the lab would get an empty key while the framework sent the default, and every check would get 401. The expression guarantees one value on both sides.
- **As built (Slice 2 fix):** the key is chosen in a "Choose the access key" step, not in job-level `env`: the runner prints each step's env header before `::add-mask::` can act, so a job-level fallback leaked once. The step reads the default from the lab's `application.yml` placeholder (single source of truth), masks it, then exports it via `$GITHUB_ENV`. The log contains the key 0 times.
- **Masking:** GitHub masks only secret values. The fallback literal is the published local key (not a secret, per architecture §3), and the framework banner prints `key=***` regardless. Add `::add-mask::` on `$API_TOKEN` in a first step as a cheap guard.
- **Sign-in details:** `MOCK_LAB_USERNAME` / `MOCK_LAB_PASSWORD` stay unset, so both sides use their documented defaults. Nothing secret is written in the YAML. The secret is never passed as `-D`, because Surefire XML records system properties.
- **Fork PRs:** they get no secrets and fall back to the local key, so they still pass.
- **`checksAtOnce`:** not set; the default is cores. The repository is **private**, so GitHub-hosted `ubuntu-24.04` runners have 2 vCPU (public repos get 4); the banner and log show the actual concurrency.

### 2.6. Tool Setup and Caching (FS §2.6)

- **Java:** `actions/checkout` and `actions/setup-java` with `distribution: temurin`, `java-version: 17`, `cache: maven`.
  - The key hashes `**/pom.xml`, which covers both the reactor and `mock-lab-app`; both share `~/.m2/repository`.
  - The wrapper's own Maven download is not cached (about 9 MB).
- **Playwright** (Screen checks only):
  1. Read `playwright.version` from the root `pom.xml` into a step output.
  2. Cache `~/.cache/ms-playwright` with `actions/cache`, key `playwright-${{ runner.os }}-<version>-<browser>`.
  3. Always run `./mvnw -B -ntp -N exec:java -Dexec.args="install --with-deps <browser>"`. On a cache hit only the apt system packages install: about 20–40 s for Chromium, 1–2 min for WebKit.
  4. Set `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` on the test step, so a missing browser fails fast instead of a silent ~1 GB download.
- **Action majors:** current majors at implementation time (checked in Slice 1: `actions/checkout@v7`, `actions/setup-java@v6`; `actions/cache@v6` checked in Slice 3; `actions/upload-artifact@v7` checked in Slice 5, latest release v7.0.1). The implementer must confirm each on the Marketplace and record them.
- **Lab dependencies in the cache (Slice 7 fix):** `setup-java` saves `~/.m2` only on a cache miss, and the first saver is Build. Build therefore also packages `mock-lab-app`, otherwise the lab's Spring Boot dependencies were never cached and every check job hit Maven Central (one run failed with HTTP 429).
- **Time limits:** `timeout-minutes: 15` on every job. The whole-run target of ≤10 min is checked in Verify; the existing Surefire `surefire.timeout=600` remains the in-job backstop for `ui-tests`.

### 2.7. Job Summary (FS §2.4)

- A step "Summarise results" runs with `if: always()` after the checks:
  `python3 .github/scripts/junit_summary.py "<Job title>" <module>/target/surefire-reports $JOB_START >> $GITHUB_STEP_SUMMARY`.
  An early step stores `JOB_START=$(date +%s)` in `$GITHUB_ENV`.
- It reads only the module's own reports, so `-am` self-checks of `common` are not double-counted.
- Skipped = `skipped` + `disabled`.
- The Build job gets the same summary for its three modules (one line each).
- **Rejected alternatives:**
  - `dorny/test-reporter` and `EnricoMi/publish-unit-test-result-action` need `checks: write` (not granted on fork PRs) and add a third-party dependency.
  - `mikepenz/action-junit-report` in summary-only mode is the closest fallback.
  - A ~60-line script owned and explained in the repo is the better answer.

### 2.8. Artifacts (FS §2.5)

| Artifact | Condition | Contents |
|---|---|---|
| `workflow-checks-results-<run_attempt>` | `if: always()` | `api-tests/target/surefire-reports/`, `api-tests/target/logs/`, `mock-lab-app/target/lab-startup.log` |
| `screen-checks-results-<browser>-<run_attempt>` | `if: always()` | the same for `ui-tests` |
| `screen-failures-<browser>-<run_attempt>` | `if: failure()`, `if-no-files-found: ignore` | `ui-tests/target/ui-failures/` (screenshot.png + trace.zip per failed check) |

- **Upload settings:** `actions/upload-artifact`, `if-no-files-found: warn` for results, no `retention-days` (GitHub's 90-day default, the user's decision). The `run_attempt` suffix avoids name clashes when a job is re-run.
- **Opening a trace:** unzip the artifact, then `./mvnw -N exec:java -Dexec.args="show-trace <path>/trace.zip"` (or trace.playwright.dev).
- **Secret safety:** env vars are not in the XML; logs are proven clean by `RunLogSecrecyTest`; traces keep the password out by `SignInTraceTest`. GitHub does not mask artifact contents, so Verify downloads and greps them.
- **Patient names in screen evidence (decided in Slice 5, FS Change Log 2026-10-05):** a failed screen check's screenshot and trace (DOM snapshots enabled) show whatever the screen showed, including made-up `TEST-patient-…` names created by parallel checks. Accepted: only synthetic, obviously fake data exists; keys and passwords never appear. Production note: restrict access to such artifacts or mask the patient column (Playwright screenshot `mask`, snapshots off).

### 2.9. README (FS §2.7)

- **Badge** under the title (the repository is private, so the badge renders only for viewers with access; the README says so):
  `[![CI](https://github.com/aleksosadchiy/labflow-test-automation/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/aleksosadchiy/labflow-test-automation/actions/workflows/ci.yml?query=branch%3Amain)`
- **New section "Continuous integration":**
  - triggers;
  - the three jobs;
  - how to start a run by hand (Actions → CI → Run workflow → family, browser);
  - where the summary and artifacts are;
  - how to open a trace;
  - the optional `API_TOKEN` repository secret;
  - how to turn on branch protection (out of scope to configure).
- **Fixes:** the tech-stack row and Phase 0/3 roadmap items are updated.

---

## 3. Impact and Risk Analysis

- **System Dependencies:**
  - Framework and lab code are unchanged.
  - Depends on GitHub-hosted `ubuntu-24.04`, Temurin 17, Maven Central, Playwright's apt dependencies.
  - Closes roadmap Phase 0 "Runnable Locally & in CI" and "Stable Test Environment", and Phase 3 "CI/CD Pipeline".
  - `architecture.md` §4–§5 get "built in spec 006" notes.

| Risk | Mitigation |
|---|---|
| Empty secret makes lab and framework keys differ, so every check gets 401 | `secrets.API_TOKEN \|\| 'mock-lab-local-token'` in one job-level `env` |
| Pre-flight makes lab-free self-checks fail in Build | Build runs only `common`, `test-data`, `api-client` tests; module self-checks run in the check jobs |
| apt mirror slowness (`--with-deps` every run) | Browser binaries cached; 15-min job timeout |
| WebKit on Linux heavier / timing differences | Manual runs only, own cache key; if flaky, recorded as a finding |
| Branch-scoped caches make the first run on a new branch slow | Accepted; the second run is checked to be faster |
| Runner image drift | Pinned `ubuntu-24.04` |
| Double runs on PR branches (push + PR) | Accepted cost, because no-cancel was chosen |
| Private repo: 2000 free Actions minutes/month, 2-vCPU runners | A run is ~5–8 runner-minutes across three jobs, far below the quota; parallel checks still work on 2 cores |
| Cannot run workflows locally (no Docker/act) | `actionlint` binary, local runs of both scripts, exact Maven commands with `ENVIRONMENT=ci`; the rest verified after the user pushes |

---

## 4. Testing Strategy

- **Local, before push** (scratchpad tools only):
  - YAML parses, and `actionlint` (darwin binary downloaded to the scratchpad) reports nothing.
  - `lab.sh start/stop` on macOS: normal start, timeout path (port 8080 already taken or a bad jar), and after `stop` nothing listens on 8080. Use an alternate port setting in the script for local checks so the engineer's own 8080 lab is never touched; the script reads `LAB_PORT` (default 8080).
  - `junit_summary.py` against real XML from a green run and from a forced-failure run: counts, failure table, all-green one-liner, no-XML message.
  - The exact check-job Maven commands with `ENVIRONMENT=ci` against a local lab are green.
- **After push** (the GitHub CLI is now authenticated in this terminal with `repo` + `workflow` scopes, so Verify tasks push branches and read runs with `gh run list/view/watch` themselves):
  - the verdict shows on the commit and on a PR;
  - two quick pushes both finish;
  - dispatch inputs (family, browser=webkit) work;
  - a deliberate screen-check failure on a throwaway branch gives a red Screen job, a green Workflow job, the failures artifact and a readable summary;
  - re-run failed jobs re-runs only that job;
  - a deliberate compile error blocks both check jobs;
  - three green runs in a row;
  - total ≤10 min;
  - the second run is faster (cache hits);
  - downloaded artifacts grep clean for the key, the password and `TEST-patient-`;
  - the badge renders.
