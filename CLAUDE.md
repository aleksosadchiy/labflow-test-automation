# Project Context for Claude Code

## What this project is

**LabFlow Test Automation** is a Java test automation framework that runs against a mock laboratory workflow (Patient → Order → Sample → Processing → Report), simulating a simplified, regulated, healthcare-adjacent domain. The full project brief is in `context/project-brief.md` — read it for the complete list of expected deliverables and quality criteria before making scope decisions.

**This is a reference project, not production software.** Every design decision should be easy to explain and defend, so:

- Prefer simple, defensible, well-explained choices over impressive-looking ones. Do not over-engineer (no Kubernetes, no huge test counts chasing 100% coverage, no production-grade auth).
- When introducing a new command, tool, or technology, briefly explain what it does and why it fits here — the project is also a learning and reference resource.
- Prioritize per the project brief's own ranking (section 30): API automation → JUnit 5 → test architecture → Page Object Model → parallel execution → test-data isolation → Maven → CI/CD → logging/reporting → Cucumber (still lowest in the brief's ranking; the behaviour-driven layer, Cucumber 7, is part of the project since spec 008, reversing on 2026-10-12 the earlier decision to leave it out).

## Always check these first

- `context/product/product-definition.md` — vision, audience, scope (in/out).
- `context/product/roadmap.md` — phased build plan (Phase 0, the mock lab backend + a tiny front-end as the system under test, through Phase 3, the CI pipeline); every item is done.
- `context/product/architecture.md` — the authoritative stack decisions and rationale. Do not introduce a different library/framework for something it already covers without updating this file (`/awos:architecture`).
- `context/product/hired-agents.md` — which specialist subagents exist and what they cover.

These are managed via AWOS (`.awos/` commands: `/awos:product`, `/awos:roadmap`, `/awos:architecture`, `/awos:hire`, `/awos:spec`, `/awos:tech`, `/awos:tasks`, `/awos:implement`, `/awos:verify`). Re-run the relevant command to update a doc rather than hand-editing it out of sync with reality.

## Specialist subagents

- **`testing-expert`** — Java test automation (JUnit 5, RestAssured, Playwright, POM). Has the `verify-ui` skill for driving/verifying a live UI (through the Playwright MCP server in `.mcp.json`).
- **`spring-boot-backend`** — the mock lab backend and its Thymeleaf UI (the system under test, not the framework under test).
- **`github-actions-cicd`** — Maven multi-module build wiring and the GitHub Actions pipeline. Has the `gha-diagnosis` skill for triaging failed CI runs.

Delegate to these rather than doing cross-cutting work generically.

## Conventions

- Java 17, Maven multi-module reactor (`common`, `test-data`, `api-client`, `api-tests`, `ui-tests`). `mock-lab-app` is deliberately not a reactor module: it is the system under test and has its own `pom.xml` and Maven Wrapper.
- No hardcoded environment values — `BASE_URL`, `API_TOKEN`, `BROWSER`, `ENVIRONMENT` via env vars / Maven system properties.
- Never log credentials, tokens, or patient-identifying data — this simulates a regulated (healthcare-adjacent) system.
- The visual (Allure) report is built only with `.github/scripts/report.sh`. Report content reuses the run log's request line; raw requests or answers are never captured into a report (no `allure-rest-assured`).
- Never commit credentials: real keys and passwords come from the environment, and `.env` files stay gitignored. CI scans the git history with gitleaks; a made-up secret in a test must start with `s3cret-` (the only allowed pattern in `.gitleaks.toml`).
- Security conventions of the lab (`mock-lab-app`): every `/api/*` route stays behind `ApiKeyFilter` and every screen behind `SessionAuthInterceptor`; request bodies bind to dedicated records in `api/dto`, never to domain classes; error responses carry fixed messages, never exception text or stack traces.
- The agent configuration is security-sensitive: `CLAUDE.md`, `.claude/` (settings, hooks, agents, skills) and `.mcp.json`. Change them only when the user asks for that change, say so in the summary, and never remove or weaken the hook in `.claude/hooks/` that blocks reading `.env` files and private keys.
- Formatting is checked by Spotless in CI. Run `./mvnw spotless:apply` (and the same inside `mock-lab-app/`) before committing.
- Dependency versions are declared once, in the root `pom.xml` properties. Do not add a dependency without recording why in `context/product/architecture.md`; Dependabot proposes minor and patch updates.
- Module dependencies point one way: `common` ← `test-data`, `common` ← `api-client` ← `api-tests` / `ui-tests`. Nothing in the reactor depends on `mock-lab-app`, and `common` and `test-data` never make HTTP calls. Checks (their step definitions) never use RestAssured or locators directly: HTTP goes through the `api-client` services, screens through the Page Objects in `ui-tests/src/main`.
- Every lab check is a Gherkin scenario, run by Cucumber through one `@Suite` runner per check module. Layout of `api-tests` and `ui-tests`: scenarios in `<module>/src/test/features` (one feature file per topic); under the test package, `steps/` (one `<Topic>Steps` class per feature), `hooks/`, `world/`, `framework/` (the machinery) and `selfcheck/` (the framework's own Jupiter tests and guards).
- Run the tests before calling a change done: `./mvnw -pl common,test-data,api-client test` needs no lab; `./mvnw clean test` needs a running lab (`cd mock-lab-app && ./mvnw spring-boot:run`); the lab's own tests are `cd mock-lab-app && ./mvnw test`. A change in behaviour ships with a test for it.
- Keep the docs true: when a command, setting, module or CI step changes, update `README.md` and the module's own `README.md` in the same change.
- Test data is generated per-test (UUID-based) for isolation under parallel execution — no shared mutable state, no order-dependent tests.
