# Project Context for Claude Code

## What this project is

A Java test automation framework built as a **reference project**. It simulates a simplified, regulated laboratory workflow (Patient → Order → Sample → Processing → Report). The full project brief is in `context/project-brief.md` — read it for the complete list of expected deliverables and evaluation criteria before making scope decisions.

**This is explicitly a portfolio/rehearsal project, not production software.** The user needs to be able to explain every design decision, so:

- Prefer simple, defensible choices over impressive-looking ones. Do not over-engineer (no Kubernetes, no huge test counts chasing 100% coverage, no production-grade auth).
- When introducing a new command, tool, or technology, briefly explain what it does and why it fits here — this project doubles as reference material.
- Prioritize per the assignment's own ranking (section 30): API automation → JUnit 5 → test architecture → Page Object Model → parallel execution → test-data isolation → Maven → CI/CD → logging/reporting → Cucumber (lowest priority).

## Always check these first

- `context/product/product-definition.md` — vision, audience, scope (in/out).
- `context/product/roadmap.md` — phased build plan; **Phase 0** stands up the mock lab backend + a tiny front-end as the system under test, since none exists yet.
- `context/product/architecture.md` — the authoritative stack decisions and rationale. Do not introduce a different library/framework for something it already covers without updating this file (`/awos:architecture`).
- `context/product/hired-agents.md` — which specialist subagents exist and what they cover.

These are managed via AWOS (`.awos/` commands: `/awos:product`, `/awos:roadmap`, `/awos:architecture`, `/awos:hire`, `/awos:spec`, `/awos:tech`, `/awos:tasks`, `/awos:implement`, `/awos:verify`). Re-run the relevant command to update a doc rather than hand-editing it out of sync with reality.

## Specialist subagents

- **`testing-expert`** — Java test automation (JUnit 5, RestAssured, Playwright, POM). Has `playwright` and `verify-ui` skills for driving/verifying a live UI.
- **`spring-boot-backend`** — the mock lab backend and its Thymeleaf UI (the system under test, not the framework under test).
- **`github-actions-cicd`** — Maven multi-module build wiring and the GitHub Actions pipeline. Has the `gha-diagnosis` skill for triaging failed CI runs.

Delegate to these rather than doing cross-cutting work generically once they exist.

## Conventions once implementation starts

- Java 17, Maven multi-module (`mock-lab-app`, `common`, `api-client`, `api-tests`, `ui-tests`, `test-data`).
- No hardcoded environment values — `BASE_URL`, `API_TOKEN`, `BROWSER`, `ENVIRONMENT` via env vars / Maven system properties.
- Never log credentials, tokens, or patient-identifying data — this simulates a regulated (healthcare-adjacent) system.
- Test data is generated per-test (UUID-based) for isolation under parallel execution — no shared mutable state, no order-dependent tests.
