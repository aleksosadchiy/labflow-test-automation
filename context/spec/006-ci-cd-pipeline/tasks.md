# Tasks: Automatic Check Runs on Every Change (Phase 3)

- **Functional Spec:** `functional-spec.md`
- **Technical Considerations:** `technical-considerations.md`

After each slice:
- the branch `spec/ci-cd-pipeline` builds locally with `./mvnw`;
- the latest pushed commit has a green CI run (or the slice's documented, deliberate red one);
- nothing under `mock-lab-app/` or the framework's Java code changes.

**Verification conventions (used by every Verify task):**

- **GitHub access.** The GitHub CLI is authenticated in this terminal (`gh`, account `aleksosadchiy`, scopes `repo` + `workflow`; `~/.local/bin/gh`), and git pushes over HTTPS through it.
  - Verify tasks push `spec/ci-cd-pipeline`, then follow the run with `gh run watch <id> --exit-status` or `gh run view <id> --log-failed`.
  - Never force-push. Never push to `main`.
- **Throwaway branches.** A deliberate failure is pushed only to a throwaway branch named `ci-probe/<what>` created from `spec/ci-cd-pipeline`. When the check is done, delete it locally and on `origin` (`git push origin --delete ci-probe/<what>`).
- **Runner minutes.** The repository is private (2000 free minutes a month, 2-vCPU runners). Do not re-run more than the task asks.
- **Local checks first.** Before pushing:
  - Lint the workflow with `actionlint`. Download the darwin binary into the session scratchpad with the official `download-actionlint.bash`; Docker and `act` are not available.
  - Exercise the scripts locally. `lab.sh` honours `LAB_PORT`, so local checks use `LAB_PORT=18080` and never touch an engineer's lab on 8080.
- **Maven locally.** Use `./mvnw` from the repo root, never `-q` when reading the `Tests run` lines, and never `install`. There is no `timeout` binary here, so use the Bash tool's own timeout.
- **Evidence.** Quote the real output for every claim: run URL, conclusion, job names and conclusions, and the key log lines from `gh run view --log`.
- **Clean-up.** Delete scratch files with literal paths, delete any downloaded artifacts, delete throwaway branches, and stop any lab you started.
- **Agent split.**
  - `github-actions-cicd` owns the workflow, the two scripts, README CI section and every Verify task. Its `gha-diagnosis` skill covers red runs.
  - `general-purpose` updates `architecture.md` and the roadmap.
  - `testing-expert` owns the final QA slice.

---

- [x] **Slice 1: Every Push Gets a Build Verdict**

  > A minimal workflow runs on every push, on PRs to `main` and on manual dispatch. One "Build" job compiles the reactor and runs the lab-free self-checks. The verdict shows on the commit. (FS §2.1 ¶1–2, §2.2 ¶4 groundwork, §2.6 ¶3 timeouts)

  - [x] Task: Create `.github/workflows/ci.yml` (name `CI`):
    - Triggers: `push` (all branches), `pull_request` to `main`, and `workflow_dispatch` with the `family` and `browser` choice inputs already declared.
    - `permissions: contents: read`. No concurrency block.
    - One job `build` ("Build") on `ubuntu-24.04`, `timeout-minutes: 15`:
      - `actions/checkout`;
      - `actions/setup-java` (temurin 17, `cache: maven`);
      - `./mvnw -B -ntp test-compile`;
      - `./mvnw -B -ntp test -pl common,test-data,api-client`.
    - Confirm the current major of each action on the Marketplace and record them in the report.
    - Lint locally with `actionlint`.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - Push the branch and watch the run: "Build" is green.
    - Quote the run URL, the `Tests run` lines from the log (`gh run view --log`), and `gh api repos/{owner}/{repo}/commits/<sha>/check-runs` showing the check run on the commit.
    - Open a draft PR `spec/ci-cd-pipeline → main` with `gh pr create --draft` (title "Spec 006: CI pipeline (draft)") and confirm the PR run appears in `gh pr checks`.
    - Leave the draft PR open for later slices.

    **[Agent: github-actions-cicd]**

- [x] **Slice 2: Workflow Checks Run Against Their Own Lab**

  > A "Workflow checks" job starts its own Mock Lab, waits for `/health`, runs the api-tests regression set plus that module's self-checks, and always stops the lab. A lab that never becomes ready fails fast with a clear message. (FS §2.2 ¶1–3, §2.3)

  - [x] Task: Write `.github/scripts/lab.sh` with `start` and `stop`, per technical considerations §2.4:
    - It honours `LAB_PORT` (default 8080).
    - `start` builds the jar and starts it in the background, with the pid and start-up log under `mock-lab-app/target/`. It polls `/health` once a second for up to 60 s, and fails early if the process dies. On timeout it prints `::error title=Lab did not start::…` and the log tail.
    - `stop` kills by pid (escalating after 10 s) and prints `Lab stopped`.
    - Make it executable (`git update-index --chmod=+x`).
    - Test locally on `LAB_PORT=18080`:
      - normal start and stop, then `lsof -i :18080` is empty;
      - the timeout path, e.g. by pointing it at a port already held by a dummy listener, or by forcing a dead process, without touching 8080.

    **[Agent: github-actions-cicd]**
  - [x] Task: Add the `api-tests` job ("Workflow checks") to `ci.yml`:
    - `needs: build`, with the `if` for `family != 'ui'` on dispatch, `timeout-minutes: 15`.
    - Job-level `env`: `ENVIRONMENT=ci`, `BASE_URL=http://localhost:8080`, `API_TOKEN: ${{ secrets.API_TOKEN || 'mock-lab-local-token' }}`, plus an `::add-mask::` step.
    - Steps:
      - "Start the Mock Lab" (`lab.sh start`);
      - `./mvnw -B -ntp -pl api-tests -am test -Dgroups="regression | !(api | ui)"`;
      - "Stop the Mock Lab" (`if: always()`).
    - Lint locally with `actionlint`.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - Push and watch: Build and Workflow checks are green.
    - Quote from the log:
      - the lab-ready line;
      - the run banner `Lab run: environment=ci … key=***`;
      - `Lab checks run: 48`;
      - the api-tests `Tests run` line;
      - `Lab stopped`.
    - Then push a throwaway `ci-probe/lab-timeout` branch whose `lab.sh` call uses an impossible readiness port. Quote the red "Workflow checks" with the `Lab did not start` annotation.
    - Delete the probe branch.

    **[Agent: github-actions-cicd]**

- [x] **Slice 3: Screen Checks Run in Their Own Job and Browser**

  > A "Screen checks (<browser>)" job runs alongside Workflow checks. It installs the browser (cached between runs), starts its own lab and runs the ui-tests regression set. (FS §2.2, §2.6 ¶2)

  - [x] Task: Add the `ui-tests` job to `ci.yml`, per technical considerations §2.6:
    - Name `Screen checks (${{ inputs.browser || 'chromium' }})`, `needs: build`, `if` for `family != 'api'`, `timeout-minutes: 15`, the same env plus `BROWSER`.
    - Read the Playwright version from the root `pom.xml`, then cache `~/.cache/ms-playwright` with key `playwright-<os>-<version>-<browser>`.
    - Run `./mvnw -B -ntp -N exec:java -Dexec.args="install --with-deps <browser>"`.
    - Set `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` on the test step.
    - Steps: Start the Mock Lab; `./mvnw -B -ntp -pl ui-tests -am test -Dgroups="regression | !(api | ui)"`; Stop the Mock Lab (`if: always()`).
    - Lint locally.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - Push and watch: all three jobs green, and Workflow checks and Screen checks ran in parallel (quote their start/finish times from `gh run view --json jobs`).
    - Quote the banner with `browser=chromium` and `Lab checks run: 16`.
    - Re-run the workflow once (`gh run rerun <id>`) and quote:
      - the cache restore line (cache hit);
      - the Screen-checks job duration for both attempts, the second being shorter or equal.

    **[Agent: github-actions-cicd]**

- [x] **Slice 4: Readable Results on the Run Page**

  > Every job writes a summary: passed/failed/skipped and duration, a one-liner when green, and a table of failing checks by plain-language name with expected-versus-actual when red. (FS §2.4)

  - [x] Task: Write `.github/scripts/junit_summary.py` (Python 3 standard library only), per technical considerations §2.7.
    - Arguments: title, reports directory, job start epoch.
    - Output: Markdown, with the all-green one-liner, the failure table (test-case name, class, first message line), and a "No results" message when no XML exists. Skipped = skipped + disabled.
    - Test it locally against:
      - real XML from a green local run of api-tests (lab on 18080);
      - XML from a forced local failure, made by temporarily breaking one check and restoring it byte-for-byte;
      - an empty directory.
    - Quote all three outputs.

    **[Agent: github-actions-cicd]**
  - [x] Task: Wire it into all three jobs.
    - An early step exports `JOB_START`.
    - A "Summarise results" step runs with `if: always()` and appends to `$GITHUB_STEP_SUMMARY`. Also echo the same Markdown to the log, so the summary can be read through `gh run view --log`.
    - Build summarises its three modules.
    - Lint locally.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - Push and watch: green. From the log, quote each job's summary block (one-liners with counts and duration).
    - Push a throwaway `ci-probe/red-screen` branch with one screen-check assertion flipped. Quote:
      - Screen checks red and Workflow checks green;
      - the run is red overall;
      - the failure table naming the check by its plain-language name with expected versus actual.
    - Keep the probe branch for Slice 5.

    **[Agent: github-actions-cicd]**

- [x] **Slice 5: Failure Evidence Kept with the Run**

  > Each check job uploads its reports, run log and lab start-up log, always. Screen checks also upload failure pictures and recordings, only on failure. Downloads contain no secrets. (FS §2.5)

  - [x] Task: Add the `actions/upload-artifact` steps, per technical considerations §2.8:
    - `workflow-checks-results-<attempt>` and `screen-checks-results-<browser>-<attempt>`, with `if: always()` and `if-no-files-found: warn`;
    - `screen-failures-<browser>-<attempt>`, with `if: failure()` and `if-no-files-found: ignore`;
    - no `retention-days`.
    - Lint locally.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - Push the branch, wait for green, then `gh run download` into the scratchpad. The results artifacts contain the surefire XML, the run log and `lab-startup.log`, and there is no `screen-failures` artifact.
    - Rebase or merge the change onto `ci-probe/red-screen`, push, and wait for the red run. `gh run download` there and confirm:
      - `screen-failures-chromium-1` holds `screenshot.png` + `trace.zip` for exactly the failed check;
      - `unzip -l` on the trace works.
    - Grep every downloaded file (including inside `trace.zip`) for `mock-lab-local-token` and `I_Like_This_Lab` (0 hits everywhere) and `TEST-patient-` (0 hits in reports and logs; allowed only inside screen-failure evidence, per the FS Change Log 2026-10-05).
    - Delete the downloads and the probe branch.

    **[Agent: github-actions-cicd]**

- [x] **Slice 6: Hand-Started Runs, the Access-Key Secret and a Broken Build**

  > A run can be started by hand for one family and either browser. The access key works from a repository secret or falls back to the local one. A failing build stops both check jobs. Re-running a failed job re-runs only that job. (FS §2.1 ¶3, §2.2 ¶3–4, §2.3 ¶4)

  - [x] Task: Make sure every dispatch path works in `ci.yml`.
    - `family=api` skips Screen checks, `family=ui` skips Workflow checks, and `browser=webkit` installs and uses WebKit with its own cache key.
    - The job names show the browser.
    - Fix anything the earlier slices left inconsistent.
    - Lint locally.

    **[Agent: github-actions-cicd]**
  - [x] Verify:
    - **Hand-started runs:**
      - `gh workflow run CI --ref spec/ci-cd-pipeline -f family=ui -f browser=webkit`: only Build and `Screen checks (webkit)` run, green, banner `browser=webkit`.
      - `gh workflow run CI --ref spec/ci-cd-pipeline -f family=api`: only Build and Workflow checks run.
    - **Access-key secret:** set the repository secret `API_TOKEN` to a random value with `gh secret set API_TOKEN`; the user approved this when reviewing the plan. Push an empty commit and confirm both check jobs are green with the secret, which proves the lab and the framework share it. Then delete the secret with `gh secret delete API_TOKEN` and confirm the next run is still green on the fallback.
    - **Broken build:** a throwaway `ci-probe/broken-build` branch with a compile error turns Build red and both check jobs `skipped`.
    - **Re-run:** `gh run rerun <red ui run id> --failed` on the earlier red-screen run (or a fresh probe) re-runs only Screen checks.
    - Delete all probe branches.

    **[Agent: github-actions-cicd]**

- [x] **Slice 7: Stable, Documented and Recorded**

  > Three green runs in a row within 10 minutes each, a README section with the status badge, and the architecture and roadmap updated. (FS §2.6 ¶1–3, §2.7)

  - [x] Task: README:
    - add the badge under the title, with a note that the private repo shows it only to people with access;
    - add a "Continuous integration" section (including one sentence that screen-failure evidence shows made-up `TEST-` patient names on purpose, and that a real regulated system would restrict or mask it): triggers, the three jobs, hand-started runs (Actions → CI → Run workflow: family, browser), where the summary and artifacts are, opening a trace, the optional `API_TOKEN` secret, and how to turn on branch protection;
    - update the CI/CD tech-stack row and any now-wrong "planned" wording about CI.
    - In `ci.yml`, give the Screen checks job a name that reads well even when the job is skipped. GitHub does not evaluate the name of a skipped job, so today it shows the raw `${{ … }}` expression. For example name it plain "Screen checks" and keep the browser in the summary title and artifact names. Lint with `actionlint`.

    **[Agent: github-actions-cicd]**
  - [x] Task: Update the docs to match what was built:
    - `context/product/architecture.md` §4–§5: built in spec 006 — the workflow, the jobs, `lab.sh`, the summary script, artifacts, the secret fallback and why it is needed, the pinned runner, private-repo notes;
    - in `context/product/roadmap.md`, tick Phase 0 "Runnable Locally & in CI" and "Stable Test Environment", and the Phase 3 "CI/CD Pipeline" items, with "_(Done in `context/spec/006-ci-cd-pipeline/`.)_" notes.

    **[Agent: general-purpose]**
  - [x] Verify:
    - Push, then let three consecutive runs on the same unchanged commit complete green: the push run plus two `gh run rerun` reruns.
    - Quote each run's conclusion and total duration (`gh run view --json createdAt,updatedAt,jobs`); each must be ≤ 10 min.
    - Confirm the README badge URL returns an SVG for the authenticated user (`gh api` on the badge or `curl` with the token header) and quote the new README section headings.

    **[Agent: github-actions-cicd]**

- [x] **Slice 8: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.
  - [x] Read functional-spec.md acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 006-ci-cd-pipeline` and `@regression` if suitable for long-term regression. **[Agent: testing-expert]**
  - [x] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: testing-expert]**
