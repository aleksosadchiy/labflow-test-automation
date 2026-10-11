# AI-SDLC Readiness Audit Report

**Date:** 2026-10-11
**Project:** labflow-test-automation
**Measurement window:** last 90 days (2026-07-13..2026-10-11)
**Coverage:** 78.0% — Average software project score among all applicable metrics by industry standards on 2026-07-06
**Points:** 351.1 pts

## Summary

| # | Dimension | Coverage | Sources | Points | FAIL | WARN | PARTIAL | PASS | SKIP |
| - | --------- | -------- | ------- | ------ | ---- | ---- | ------- | ---- | ---- |
| 1 | Delivery Flow | 75.0% | GitHub PRs via gh, git history | 34.5 | 1 | 0 | 2 | 3 | 1 |
| 2 | Software Best Practices | 40.7% | source code | 11 | 3 | 2 | 0 | 2 | 2 |
| 3 | Code Architecture | 88.9% | source code | 24 | 1 | 0 | 0 | 6 | 0 |
| 4 | Quality Assurance | 92.6% | source code | 28.7 | 0 | 1 | 0 | 7 | 3 |
| 5 | Application Security (OWASP ASVS 5.0.0) | 56.9% | source code | 33 | 3 | 3 | 0 | 3 | 5 |
| 6 | Supply Chain Security | 69.2% | source code | 18 | 1 | 0 | 0 | 3 | 4 |
| 7 | AI Security | 100.0% | source code | 53 | 0 | 0 | 0 | 7 | 0 |
| 8 | Documentation Quality | 61.0% | source code | 18.3 | 1 | 1 | 2 | 3 | 0 |
| 9 | AI Development Tooling | 100.0% | source code | 29 | 0 | 0 | 0 | 7 | 0 |
| 10 | Spec-Driven Development | 88.2% | source code | 30 | 0 | 2 | 0 | 5 | 0 |
| 11 | AI-SDLC Adoption | 95.1% | GitHub Actions via gh, git history | 46.6 | 0 | 0 | 3 | 6 | 6 |
| 12 | Prevention Coverage | 62.5% | source code | 25 | 5 | 0 | 0 | 11 | 0 |
| 13 | Descriptors | info | git history, source code (AST) | — | 0 | 0 | 0 | 0 | 1 |

## Recommendations

| # | Priority | Dimension | Check | Effort | What to do |
| - | -------- | --------- | ----- | ------ | ---------- |
| 1 | P0 | Delivery Flow | DF-05 | — | Review rounds and time-to-resolve-review-threads per PR; proxied from post-open commits when no host data is available |
| 2 | P0 | Software Best Practices | SBP-01 | — | Code linters are configured for all major languages in the project with runnable scripts |
| 3 | P0 | Software Best Practices | SBP-02 | — | Code formatting is automated and consistent via formatters with scripts or pre-commit hooks |
| 4 | P0 | Software Best Practices | SBP-05 | — | Dependencies are locked with lockfiles and dependency update automation is configured |
| 5 | P0 | Code Architecture | ARCH-05 | — | The project follows consistent naming conventions for files and directories. All-or-nothing (AWOS's own standard — no source publishes an acceptable inconsistency rate): every file must follow the dominant convention. |
| 6 | P0 | Application Security (OWASP ASVS 5.0.0) | AS-02 | — | HTTP security headers (Strict-Transport-Security, X-Content-Type-Options, CSP frame-ancestors or X-Frame-Options) are configured per ASVS 5.0.0 (3.4.1 L1; 3.4.4, 3.4.6 L2) |
| 7 | P0 | Application Security (OWASP ASVS 5.0.0) | AS-06 | — | State-changing HTTP endpoints (POST/PUT/PATCH/DELETE) require authentication; decorator/middleware patterns are present per ASVS 5.0.0 L1 (8.3.1). ASVS requires 100% coverage; the PASS >=70% / WARN >=30% detection steps are AWOS calibration for noisy static detection. |
| 8 | P0 | Application Security (OWASP ASVS 5.0.0) | AS-09 | — | Authentication and public endpoints show evidence of rate limiting or brute-force protection per ASVS 5.0.0 L1 (6.3.1) |
| 9 | P0 | Supply Chain Security | SCS-01 | — | Lockfiles exist and are tracked by git, ensuring all developers and CI use identical resolved dependency trees |
| 10 | P0 | Documentation Quality | DOC-03 | — | API endpoints are documented via OpenAPI/Swagger specs or equivalent, proportional to API surface |

## Prevention Matrix

Whether today’s state is protected against regression: each failure-mode cluster is **enforced** (a mechanical gate blocks recurrence), **instructed** (an agent-visible written rule), or **absent** (nothing prevents recurrence).

| Cluster | Tier | Enforcement | Instruction | At risk | Unguarded passes |
| ------- | ---- | ----------- | ----------- | ------- | ---------------- |
| Secrets hygiene | ENFORCED | PRV-01 (PASS) | PRV-11 (PASS) | AS-05 | — |
| Dependency supply chain | ENFORCED | PRV-02 (PASS) | PRV-12 (PASS) | SCS-01 | — |
| Appsec design | INSTRUCTED | PRV-03 (FAIL) | PRV-13 (PASS) | AS-02, AS-06, AS-08, AS-09, AS-11 | — |
| Code style | INSTRUCTED | PRV-04 (FAIL) | PRV-14 (PASS) | SBP-01, SBP-02, SBP-03 | — |
| Architecture boundaries | INSTRUCTED | PRV-05 (FAIL) | PRV-15 (PASS) | — | — |
| Testing discipline | INSTRUCTED | PRV-06 (FAIL) | PRV-16 (PASS) | QA-07 | — |
| Ai file integrity | ENFORCED | PRV-07 (PASS) | PRV-17 (PASS) | — | — |
| Docs freshness | INSTRUCTED | PRV-08 (FAIL) | PRV-18 (PASS) | DOC-03, DOC-07 | — |

## Dimension: Delivery Flow

> DORA-style delivery-flow metrics — deployment frequency, lead time, PR cycle time, change-failure rate, review rework, rework rate, and MTTR — computed from git history (and connectors when available)

**Score:** 34.5 pts (coverage 75.0% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | DF-01 | computed | 3.6 | 10 | PARTIAL | not-reliable (HIGH) | 100% | 0.62 | Merges into the default branch per week (DORA deployment frequency proxy) · computed · DORA Accelerate State of DevOps 2023 (2023-12) — [DORA Accelerate State of DevOps 2023 2023-12](https://dora.dev/research/2023/dora-report/2023-dora-accelerate-state-of-devops-report.pdf) |
| 2 | DF-02 | computed | 7.9 | 10 | PARTIAL | minimal (HIGH) * | 100% | 13.99 | Median time from first commit on a branch to its merge into the default branch · computed · DORA Accelerate State of DevOps 2023 (2023-12) — [DORA Accelerate State of DevOps 2023 2023-12](https://dora.dev/research/2023/dora-report/2023-dora-accelerate-state-of-devops-report.pdf) |
| 3 | DF-03 | computed | 3 | 3 | PASS | maximal (HIGH) | 100% | 0.03 | Time from PR open to merge; low-confidence (git-approximation) when no code-host connector is available · computed · DORA Accelerate State of DevOps 2023 (2023-12) — [DORA Accelerate State of DevOps 2023 2023-12](https://dora.dev/research/2023/dora-report/2023-dora-accelerate-state-of-devops-report.pdf) |
| 4 | DF-04 | computed | 10 | 10 | PASS | minimal (HIGH) * | 100% | 0 | Share of the window's default-branch merge events that are reverts, hotfixes, or rollbacks (keyword proxy for the DORA change-failure rate) · computed · DORA Accelerate State of DevOps 2023 (2023-12) — [DORA Accelerate State of DevOps 2023 2023-12](https://dora.dev/research/2023/dora-report/2023-dora-accelerate-state-of-devops-report.pdf) |
| 5 | DF-05 | computed | 0 | 3 | FAIL | minimal (HIGH) * | 100% | 9.88 | Review rounds and time-to-resolve-review-threads per PR; proxied from post-open commits when no host data is available · computed · DX Core 4 (2024-12) — [DX Core 4 2024-12](https://getdx.com/research/measuring-developer-productivity-with-the-dx-core-4/) |
| 6 | DF-06 | computed | 10 | 10 | PASS | minimal (HIGH) * | 100% | 0 | DORA deployment rework rate: ratio of deployments that are unplanned work to fix bugs triggered by incidents in production. Proxied from git: first-parent merges in the audit window ([meta].max_lookback_days) whose subject matches fix/bugfix/hotfix/patch/defect/regression keywords (case-insensitive). Distinct from change_failure_rate (DF-04), which counts revert/rollback deployments, and from code_churn (DESC-02), which measures line-level turnover. DORA publishes no numeric thresholds for rework rate; bands (good <15%, watch <30%, concerning >=30%) are AWOS heuristics. · computed · DORA (2026-01) — [DORA 2026-01](https://dora.dev/guides/dora-metrics/) |
| 7 | DF-07 | computed | 0 | 5 | SKIP | — | — | git-proxy, true value may differ | Median time to recovery from incidents, measured from a real incident source (PagerDuty/OpsGenie/incident.io, Statuspage, or code-host incident labels) via the incidents collector; awarded when at least one incident has a resolved recovery span. Without a measurable recovery span, MTTR reports a git proxy and this category stays SKIP. · computed · DORA Accelerate State of DevOps 2023 (2023-12) — [DORA Accelerate State of DevOps 2023 2023-12](https://dora.dev/research/2023/dora-report/2023-dora-accelerate-state-of-devops-report.pdf) |

`*` lower-bound measurement (reliability tag: `minimal`).

## Dimension: Software Best Practices

> Evaluates code quality, architecture patterns, and engineering standards

**Score:** 11 pts (coverage 40.7% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | SBP-01 | detected | 0 | 5 | FAIL | maximal (HIGH) | 100% | 0 | Code linters are configured for all major languages in the project with runnable scripts · detected · Martin Fowler CI/CD (2024-01-18) · prevention: instructed (code-style) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 2 | SBP-02 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Code formatting is automated and consistent via formatters with scripts or pre-commit hooks · detected · Martin Fowler CI/CD (2024-01-18) · prevention: instructed (code-style) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 3 | SBP-03 | detected | 2.5 | 5 | WARN | maximal (HIGH) | 100% | 33 | The project uses strong typing where the language supports it — e.g. strict-mode type config or a high ratio of type annotations in sampled source. The >=60% PASS / >=25% WARN typed-share thresholds are AWOS heuristics — gradual-typing guidance publishes no percentages. · detected · Martin Fowler CI/CD (2024-01-18) · prevention: instructed (code-style) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 4 | SBP-04 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 1 | Automated build, test, and deploy pipeline is configured with build and test stages · detected · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 5 | SBP-05 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Dependencies are locked with lockfiles and dependency update automation is configured · detected · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 6 | SBP-06 | detected | 2.5 | 5 | WARN | maximal (HIGH) | 100% | 6 | The codebase follows consistent error handling — errors are logged or propagated, not silently swallowed. The FAIL >=50% / WARN >=10% bad-catch shares are AWOS's own standard — the literature treats silent catches as never acceptable and publishes no tolerated rate. · detected · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 7 | SBP-07 | detected | 1 | 1 | PASS | maximal (HIGH) | 100% | 0 | Python source contains no Python-2 except-clause syntax (except A, B:), which is a SyntaxError under Python 3 (Python repos only) · detected · Python 3 — What's New (PEP 3110) (2008-12) — [Python 3 — What's New (PEP 3110) 2008-12](https://docs.python.org/3/whatsnew/3.0.html) |
| 8 | SBP-08 | computed | 0 | 5 | SKIP | — | — | fewer than 2 architectural layers present — vertical delivery not applicable | Feature branches touch multiple layers in a single branch, indicating vertical delivery. The PASS >=50% / WARN >=25% cross-layer branch shares are AWOS heuristics — vertical-slice guidance is qualitative. · computed · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 9 | SBP-09 | detected | 0 | 3 | SKIP | — | — | fewer than 2 distinct layers detected — single-layer project, SBP-09 not applicable; API layer: not detected; UI layer: not detected; DB layer: not detected | API definitions have corresponding UI consumers and database schemas have corresponding API layers · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |

## Dimension: Code Architecture

> Evaluates whether the codebase follows a coherent architectural pattern with clear module boundaries and SRP

**Score:** 24 pts (coverage 88.9% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | ARCH-01 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 3 | The project follows a declared or recognizable architectural pattern · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 2 | ARCH-02 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 0 | Code modules have clear boundaries and no tangled cross-imports · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 3 | ARCH-03 | judgment | 3 | 3 | PASS | maximal (MED) | 85% | 6 modules, 0 god modules | Modules and directories serve a single clear purpose rather than being catch-all dumping grounds · judgment · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 4 | ARCH-04 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 11 | Business logic, data access, and presentation are separated — not mixed in the same files · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 5 | ARCH-05 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0.94 | The project follows consistent naming conventions for files and directories. All-or-nothing (AWOS's own standard — no source publishes an acceptable inconsistency rate): every file must follow the dominant convention. · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 6 | ARCH-06 | computed | 3 | 3 | PASS | maximal (HIGH) | 100% | 0.01 | Source files are reasonably sized, indicating proper decomposition · computed · McCabe 1976 (1976-07) — [McCabe 1976 1976-07](https://doi.org/10.1109/TSE.1976.233837) |
| 7 | ARCH-07 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 1 | The project has cross-layer tooling (unified Makefile, docker-compose, shared CI) enabling full-stack development · detected · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |

## Dimension: Quality Assurance

> Evaluates testing pyramid structure, tooling maturity, and optional contract/ML testing readiness

**Score:** 28.7 pts (coverage 92.6% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | QA-01 | computed | 7.2 | 8 | PASS | maximal (HIGH) | 100% | 0.89 | The project has meaningful tests covering at least 60% of its source modules. The 60% PASS / 30% WARN thresholds are AWOS calibration of a module-has-tests share — Fowler's pyramid publishes no coverage numbers and argues against coverage targets (Google's published 60/75/90 bands apply to line coverage, a different metric). · computed · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 2 | QA-02 | computed | 0 | 3 | SKIP | — | — | missing sources: scale (no parseable coverage report found (lcov.info, cobertura/coverage.xml, coverage-summary.json, jacoco.xml, clover.xml) — run the test suite with coverage locally or in CI so a report exists to measure) | Measured line/statement coverage parsed from coverage reports on disk (lcov, cobertura, istanbul summary, JaCoCo, clover), scored against Google's published guidance: 60% acceptable, 75% commendable, 90% exemplary. Distinct from QA-01 (module-has-tests proxy) and QA-03 (coverage measurement presence). Per the same source, a coverage percentage must never be a sole quality target. SKIP when no report exists. · computed · Google Testing Blog — Code Coverage Best Practices (2020-08-07) — [Google Testing Blog — Code Coverage Best Practices 2020-08-07](https://testing.googleblog.com/2020/08/code-coverage-best-practices.html) |
| 3 | QA-03 | detected | 1 | 1 | PASS | maximal (HIGH) | 100% | 1 | The project measures what percentage of source code is exercised by tests, optionally enforcing a minimum threshold · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 4 | QA-04 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 127 | The project has tests that verify individual units of logic in isolation, without real I/O or external dependencies · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 5 | QA-05 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 5 | The project has tests that verify interactions between components across real databases, HTTP calls, or message queues · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 6 | QA-06 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 5 | The project has end-to-end tests exercising complete user flows through a real UI, API surface, or CLI · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 7 | QA-07 | computed | 1.5 | 3 | WARN | maximal (HIGH) | 100% | 95 | Test distribution follows a healthy pyramid: most tests unit-level, fewer integration, fewest E2E · computed · Martin Fowler — Practical Test Pyramid (2018-02) · prevention: instructed (testing-discipline) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 8 | QA-08 | detected | 1 | 1 | PASS | maximal (HIGH) | 100% | 1 | Tests use a structured approach to create and manage test data rather than scattering hardcoded inline values · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 9 | QA-09 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 2 | Unit and integration tests use mocking/stubbing to isolate the code under test from external dependencies · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 10 | QA-10 | detected | 0 | 5 | SKIP | — | — | Not applicable — "topology.is_multi_service" is false for this repository. | Service boundaries are verified through consumer-driven contract tests ensuring producers do not break consumers · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |
| 11 | QA-11 | detected | 0 | 5 | SKIP | — | — | Not applicable — "topology.has_ml_layer" is false for this repository. | ML models are tested for quality metrics as part of the development cycle, not just functional correctness · detected · Martin Fowler — Practical Test Pyramid (2018-02) — [Martin Fowler — Practical Test Pyramid 2018-02](https://martinfowler.com/articles/practical-test-pyramid.html) |

## Dimension: Application Security (OWASP ASVS 5.0.0)

> Audits application-layer security against a curated OWASP ASVS 5.0.0 subset (Level-1 core plus a few Level-2 requirements) — TLS, security headers, CORS, SQL injection, hardcoded secrets, auth on mutations, password hygiene, input validation, rate limiting, authorization — plus secret-file hygiene (.env gitignored, env templates, sensitive-file ignore coverage)

**Score:** 33 pts (coverage 56.9% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | AS-01 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | The application enforces TLS — no plain-HTTP service URLs are present in config files per ASVS 5.0.0 L1 (12.2.1) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 2 | AS-02 | detected | 0 | 5 | FAIL | maximal (HIGH) | 100% | 0 | HTTP security headers (Strict-Transport-Security, X-Content-Type-Options, CSP frame-ancestors or X-Frame-Options) are configured per ASVS 5.0.0 (3.4.1 L1; 3.4.4, 3.4.6 L2) · detected · OWASP ASVS (2025-05-30) · prevention: instructed (appsec-design) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 3 | AS-03 | detected | 0 | 5 | SKIP | — | — | no CORS configuration keyword found — browsers default to same-origin; check skipped | CORS is not configured with a wildcard origin ('*') per ASVS 5.0.0 L1 (3.4.2) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 4 | AS-04 | detected | 0 | 8 | SKIP | — | — | Not applicable — "topology.has_db" is false for this repository. | Database queries use parameterized statements or an ORM; no string-concatenated SQL is detected per ASVS 5.0.0 L1 (1.2.4) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 5 | AS-05 | detected | 4 | 8 | WARN | maximal (HIGH) | 100% | 2 | No hardcoded secrets, API keys, or credentials appear in committed source files per ASVS 5.0.0 (13.3.1, L2) · detected · OWASP ASVS (2025-05-30) · prevention: enforced (secrets-hygiene) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 6 | AS-06 | detected | 0 | 8 | FAIL | maximal (HIGH) | 100% | 0 | State-changing HTTP endpoints (POST/PUT/PATCH/DELETE) require authentication; decorator/middleware patterns are present per ASVS 5.0.0 L1 (8.3.1). ASVS requires 100% coverage; the PASS >=70% / WARN >=30% detection steps are AWOS calibration for noisy static detection. · detected · OWASP ASVS (2025-05-30) · prevention: instructed (appsec-design) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 7 | AS-07 | detected | 0 | 5 | SKIP | — | — | no password-hashing or session-token patterns found — hygiene check skipped | Password hashing uses a strong algorithm (bcrypt/argon2/scrypt) per ASVS 5.0.0 11.4.2 (L2), and session tokens are generated with a CSPRNG per the ASVS 5.0.0 V7.2 session-token requirements · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 8 | AS-08 | detected | 2.5 | 5 | WARN | maximal (HIGH) | 100% | 0 | Request input is validated or sanitized before processing; known validation libraries or decorators are referenced per ASVS 5.0.0 V2.2 (validation and business logic) · detected · OWASP ASVS (2025-05-30) · prevention: instructed (appsec-design) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 9 | AS-09 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Authentication and public endpoints show evidence of rate limiting or brute-force protection per ASVS 5.0.0 L1 (6.3.1) · detected · OWASP ASVS (2025-05-30) · prevention: instructed (appsec-design) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 10 | AS-10 | judgment | 8 | 8 | PASS | maximal (MED) | 80% | 8 of 8 state-changing endpoints guarded (6 need authentication; 2 are auth-flow endpoints that are open by design) | Authorization checks are applied consistently to all protected resources; no privilege-escalation paths are evident per ASVS 5.0.0 L1 (8.3.1) · judgment · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 11 | AS-11 | judgment | 2.5 | 5 | WARN | maximal (MED) | 70% | 1 of 4 insecure-design patterns present (object-level access control) | The overall HTTP API design does not exhibit systemic insecure-design patterns per OWASP Top 10 2021 (A04: Insecure Design; ASVS 5.0 removed the secure-SDLC chapter) · judgment · OWASP Top 10 (2021-09) · prevention: instructed (appsec-design) — [OWASP Top 10 2021-09](https://owasp.org/Top10/A04_2021-Insecure_Design/) |
| 12 | AS-12 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | Environment files containing secrets are excluded from version control via .gitignore (nearest ASVS 5.0.0 requirement: 13.3.1 secrets management) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 13 | AS-13 | detected | 0 | 5 | SKIP | — | — | Not applicable — "topology.uses_env_vars" is false for this repository. | A template environment file exists so developers know which variables to configure (nearest ASVS 5.0.0 requirement: 13.3.1 secrets management) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |
| 14 | AS-14 | detected | 0 | 5 | SKIP | — | — | no sensitive file types present in this stack — ignore-coverage check not applicable | Sensitive file types present in the stack are excluded from version control AND from container image builds (.gitignore + .dockerignore); inconsistency that would leak a secret into an image is flagged (nearest ASVS 5.0.0 requirement: 13.3.1 secrets management) · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |

## Dimension: Supply Chain Security

> Detects supply chain attack vulnerabilities in package dependencies — lockfile integrity, version pinning, quarantine periods, vulnerability scanning, and dependency bloat

**Score:** 18 pts (coverage 69.2% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | SCS-01 | detected | 0 | 8 | FAIL | maximal (HIGH) | 100% | 0 | Lockfiles exist and are tracked by git, ensuring all developers and CI use identical resolved dependency trees · detected · OWASP SCVS (2020) · prevention: enforced (dependency-supply-chain) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 2 | SCS-02 | detected | 0 | 5 | SKIP | — | — | Not applicable — "topology.has_lockfiles" is false for this repository. | Lockfiles include cryptographic integrity hashes verifying downloaded packages match resolved dependencies · detected · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 3 | SCS-03 | detected | 0 | 5 | SKIP | — | — | no package manifests found — pinned-version check skipped | Dependency versions use exact pinning — not wildcards, open-ended ranges, or caret/tilde ranges allowing silent re-resolution. The >=30% FAIL / >=10% WARN unpinned-share thresholds are AWOS heuristics — OWASP SCVS publishes no pinning percentage. · detected · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 4 | SCS-04 | computed | 0 | 8 | SKIP | — | — | SCS-04 (quarantine-age) requires live registry API calls to resolve per-version publish timestamps; This check is non-deterministic offline — it is intentionally skipped by the static detector; To evaluate: query npm/PyPI/crates.io registry APIs and verify each pinned version is ≥7 days old | All resolved dependency versions have been published for at least 7 days, reducing supply chain attack exposure. The 7-day window matches pnpm's documented one-week minimumReleaseAge option; pnpm's default is 1 day — the stricter choice is AWOS calibration. · computed · pnpm — Supply chain security (minimumReleaseAge) (2025) — [pnpm — Supply chain security (minimumReleaseAge) 2025](https://pnpm.io/supply-chain-security) |
| 5 | SCS-05 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 1 | Dependency update PRs require human review before merging, preventing automated merging of potentially compromised updates · detected · NIST SSDF (SP 800-218) (2022-02) — [NIST SSDF (SP 800-218) 2022-02](https://csrc.nist.gov/pubs/sp/800/218/final) |
| 6 | SCS-06 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 2 | The CI/CD pipeline includes automated vulnerability scanning of dependencies catching known CVEs before production · detected · NIST SSDF (SP 800-218) (2022-02) — [NIST SSDF (SP 800-218) 2022-02](https://csrc.nist.gov/pubs/sp/800/218/final) |
| 7 | SCS-07 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 0 | Dependency version overrides are tracked, minimal, and justified — present overrides are surfaced for human review (freshness/CVE status is not verified offline) · detected · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 8 | SCS-08 | computed | 0 | 3 | SKIP | — | — | Not applicable — "topology.has_lockfiles" is false for this repository. | The dependency tree is not excessively bloated — direct-to-transitive ratio is within healthy range for the ecosystem · computed · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |

## Dimension: AI Security

> AI-specific security — malicious or suspicious content in agent definitions, skills, hooks, MCP configs and command files, plus guardrails restricting agent access to secrets

**Score:** 53 pts (coverage 100.0% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | AIS-01 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 0 | AI agent instruction files contain no invisible Unicode characters that could hide malicious instructions · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 2 | AIS-02 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 0 | Agent instruction files contain no prompt injection patterns attempting to override behavior or exfiltrate data · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 3 | AIS-03 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | Hook scripts contain only legitimate automation with no exfiltration, obfuscation, or download-and-execute patterns · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 4 | AIS-04 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | MCP server configurations reference trusted, verifiable endpoints with no bare IPs, HTTP remote URLs, or embedded credentials · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 5 | AIS-05 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 24 | All AI agent instruction and configuration files are tracked in git, providing auditable change history · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 6 | AIS-06 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 17 | Skill and command files contain no instructions to bypass security controls, access secrets, or modify security configuration · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 7 | AIS-07 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | Agentic coding tool hooks are configured to prevent agents from reading sensitive files · detected · OWASP ASVS (2025-05-30) — [OWASP ASVS 2025-05-30](https://owasp.org/www-project-application-security-verification-standard/) |

## Dimension: Documentation Quality

> Verifies that project documentation is accurate, complete, and maintainable

**Score:** 18.3 pts (coverage 61.0% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | DOC-01 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 23015 | The repository has a top-level README.md with setup instructions a new developer could follow · detected · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 2 | DOC-02 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 6 | Each major service directory has its own README.md with build and run instructions. All-or-nothing (AWOS's own standard — sources treat per-service READMEs as universally expected, publishing no acceptable share): every service directory needs one. · detected · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 3 | DOC-03 | detected | 0 | 5 | FAIL | maximal (HIGH) | 100% | 0 | API endpoints are documented via OpenAPI/Swagger specs or equivalent, proportional to API surface · detected · OpenAPI Specification (2025-09-19) · prevention: instructed (docs-freshness) — [OpenAPI Specification 2025-09-19](https://spec.openapis.org/oas/latest.html) |
| 4 | DOC-04 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 8 | Documentation references match current code reality — sampled claims are accurate · detected · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 5 | DOC-05 | computed | 0.9 | 3 | PARTIAL | maximal (HIGH) | 100% | 0.30 | Public/exported definitions carry doc-comments (docstring/JSDoc/KDoc/Go doc) — coverage of the documented API surface · computed · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 6 | DOC-06 | computed | 0.9 | 3 | PARTIAL | maximal (HIGH) | 100% | 0.30 | Overall definition doc-comment coverage across all source definitions · computed · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 7 | DOC-07 | detected | 2.5 | 5 | WARN | maximal (HIGH) | 100% | 1 | Specifications link to implementation and implementation references specs bidirectionally · detected · AWOS convention (2026-06) · prevention: instructed (docs-freshness) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |

## Dimension: AI Development Tooling

> Checks AI-agent infrastructure — CLAUDE.md quality, agent configs, skills, MCP servers, hooks, and commands

**Score:** 29 pts (coverage 100.0% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | AI-01 | judgment | 8 | 8 | PASS | maximal (MED) | 70% | 1 instruction file (CLAUDE.md, 35 lines) plus 3 agent definitions and 2 skills; 6 non-obvious facts sampled | The combined agent instruction file ecosystem gives AI agents sufficient non-obvious context to work effectively in the project · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 2 | AI-02 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 9 | The project defines custom slash commands for common workflows · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 3 | AI-03 | detected | 1 | 1 | PASS | maximal (HIGH) | 100% | 2 | The project uses agentic coding tool skills for specialized workflows · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 4 | AI-04 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 1 | The project configures MCP servers for extended tool access · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 5 | AI-05 | detected | 1 | 1 | PASS | maximal (HIGH) | 100% | 1 | The project uses agentic coding tool hooks for automated guardrails or workflows · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 6 | AI-06 | judgment | 5 | 5 | PASS | maximal (MED) | 65% | 1 instruction file evaluated, 5 lines passing the key test | Every agent instruction file contains high-quality, non-obvious content that actually helps AI agents · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 7 | AI-07 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | The agentic coding tool has the means to run the application and observe results without human involvement · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |

## Dimension: Spec-Driven Development

> Checks that the project uses AWOS for spec-driven development and that specs, architecture, and task assignments are healthy

**Score:** 30 pts (coverage 88.2% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | SDD-01 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 2 | AWOS framework is installed in the project providing the spec-driven development workflow · detected · AWOS convention (2026-06) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |
| 2 | SDD-02 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 3 | The three foundational AWOS documents exist with substantive content: product definition, roadmap, and architecture · detected · AWOS convention (2026-06) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |
| 3 | SDD-03 | detected | 2.5 | 5 | WARN | maximal (HIGH) | 100% | 2 | Technology choices declared in architecture.md match what is actually used in the codebase · detected · AWOS convention (2026-06) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |
| 4 | SDD-04 | computed | 8 | 8 | PASS | maximal (HIGH) | 100% | 0.88 | Significant features are built through a spec-driven workflow (any framework — AWOS, Kiro, Agent-OS, and similar), not ad-hoc prompting — 70%+ of merged FEATURE PRs/branches touched spec files (fixes, reverts, and chores are excluded: maintenance needs no spec). The 70% PASS / 40% WARN thresholds are AWOS heuristics — Spec Kit publishes no adoption bands. · computed · GitHub — Spec-driven development with AI (Spec Kit) (2025-09-02) — [GitHub — Spec-driven development with AI (Spec Kit) 2025-09-02](https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/) |
| 5 | SDD-05 | detected | 2 | 2 | PASS | maximal (HIGH) | 100% | 6 | Each spec directory contains the full AWOS spec triad: functional-spec.md, technical-considerations.md, and tasks.md · detected · AWS Kiro — Specs (2025) — [AWS Kiro — Specs 2025](https://kiro.dev/docs/specs/) |
| 6 | SDD-06 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 0 | Specs that have progressed past Draft are actively being worked on, not abandoned mid-workflow · detected · AWOS convention (2026-06) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |
| 7 | SDD-07 | detected | 1.5 | 3 | WARN | maximal (HIGH) | 100% | 0.44 | Tasks in tasks.md files are annotated with meaningful agent assignments using the AWOS format. The 70% PASS / 40% WARN annotation-share thresholds are AWOS heuristics. · detected · AWOS convention (2026-06) — [AWOS convention 2026-06](https://github.com/provectus/awos/blob/main/README.md) |

## Dimension: AI-SDLC Adoption

> Measures the team's quantitative adoption of AI-augmented delivery practices — tooling depth, AI attribution, CI health, ticket work-mix, and spec coverage

**Score:** 46.6 pts (coverage 95.1% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | ADP-01 | detected | 10 | 10 | PASS | maximal (HIGH) | 100% | 1 | Repo has a non-trivial agent instruction file (CLAUDE.md / AGENTS.md / GEMINI.md / .cursorrules / .github/copilot-instructions.md or equivalent) providing AI agent context · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 2 | ADP-02 | detected | 6 | 6 | PASS | maximal (HIGH) | 100% | 1 | Repo defines agent skill files (e.g. .claude/skills/*/SKILL.md or equivalent for other AI coding tools) · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 3 | ADP-03 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 1 | Repo defines custom commands or rule files for an agentic coding tool (e.g. .claude/commands/, .cursor/rules/, .gemini/commands/) · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 4 | ADP-04 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 1 | Repo defines lifecycle hooks for an agentic coding tool (e.g. .claude/hooks/, .kiro/hooks/) · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 5 | ADP-05 | detected | 5 | 5 | PASS | maximal (HIGH) | 100% | 1 | Repo carries an MCP server config (.mcp.json or equivalent) · detected · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 6 | ADP-06 | detected | 8 | 8 | PASS | maximal (HIGH) | 100% | 1 | Repo has spec-driven adoption signals: spec directories, spec-referencing hooks/scripts, or spec docs · detected · GitHub — Spec-driven development with AI (Spec Kit) (2025-09-02) — [GitHub — Spec-driven development with AI (Spec Kit) 2025-09-02](https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/) |
| 7 | ADP-07 | computed | 1.8 | 2 | PARTIAL | minimal (HIGH) * | 100% | 0.91 | Share of commits/PRs in the audit window carrying AI markers (Co-authored-by: trailer, agent label) — current practice, not historical penetration. Always a lower bound — true usage >= shown. · computed · GitHub Docs — Co-authored-by (2025) — [GitHub Docs — Co-authored-by 2025](https://docs.github.com/en/pull-requests/committing-changes-to-your-project/creating-and-editing-commits/creating-a-commit-with-multiple-authors) |
| 8 | ADP-08 | computed | 4.3 | 5 | PARTIAL | not-reliable (HIGH) | 100% | 0.86 | Default-branch CI pass rate over decided runs; feature-branch failures excluded; treated as supporting evidence, not headline. The score is the pass rate itself; band labels (elite >=99%) are AWOS heuristics — Fowler publishes no numeric targets. · computed · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 9 | ADP-09 | computed | 0 | 3 | SKIP | — | — | 51 CI runs present but no decided run carries duration_seconds — cannot compute pipeline duration | Feedback-loop speed: average pipeline duration over decided runs in the window. Scored via the declared log curve (full score at Fowler's 10-minute build, zero at 2h); all but the 10-minute anchor are AWOS heuristics. · computed · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 10 | ADP-10 | computed | 0 | 5 | SKIP | — | — | missing sources: tracker | Team capacity split across Growth (new features/value), KTLO (Keep The Lights On — maintenance, bug fixes, dependency and infra upkeep), and Support (customer-driven) work. · computed · DX Core 4 (whitepaper) (2024-12) — [DX Core 4 (whitepaper) 2024-12](https://getdx.com/uploads/dx-core-4.pdf) |
| 11 | ADP-11 | computed | 0 | 3 | SKIP | — | — | missing sources: tracker | Delivered-issue count and backlog burn-down rate per monthly bucket · computed · DX Core 4 (whitepaper) (2024-12) — [DX Core 4 (whitepaper) 2024-12](https://getdx.com/uploads/dx-core-4.pdf) |
| 12 | ADP-12 | computed | 0 | 3 | SKIP | — | — | missing sources: tracker | Over-splitting tickets into many subtasks fragments work — departs from INVEST right-sizing, adds coordination/context-switching cost; bands are AWOS heuristics, DORA/INVEST publish no numeric threshold · computed · Agile Alliance (INVEST) (2003) — [Agile Alliance (INVEST) 2003](https://agilealliance.org/glossary/invest/) |
| 13 | ADP-13 | computed | 0 | 3 | SKIP | — | — | missing sources: tracker | Share of tickets with a non-trivial description (≥50 chars) AND acceptance criteria; thin tickets starve both humans and AI agents of context. The 50-char threshold is an AWOS heuristic — Agile Alliance's Definition of Ready specifies acceptance criteria but publishes no numeric character-count threshold. Size/structure signals only; no raw description text is stored. · computed · Agile Alliance (Definition of Ready) (2012) — [Agile Alliance (Definition of Ready) 2012](https://www.agilealliance.org/glossary/definition-of-ready/) |
| 14 | ADP-14 | computed | 0 | 5 | SKIP | — | — | missing sources: docs | External spec/doc coverage & freshness in Confluence/Coda/Notion; strengthens tooling_depth spec signal · computed · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 15 | ADP-15 | computed | 1.5 | 3 | PARTIAL | minimal (HIGH) * | 100% | 0.50 | Onboarding enabler presence as a proxy for the DX Core 4 'Time to 10th PR' outcome (average time for a new engineer to merge their 10th pull request). Four boolean signals are checked: (1) README contains a setup/install/getting-started/usage/quickstart heading or a common bootstrap command (npm install, yarn, pnpm install, make, docker compose, pip install, poetry install, ./gradlew, bundle install, go build/run, cargo build, uv sync); (2) agent context file present (CLAUDE.md/AGENTS.md or topology.has_agent_instruction_files); (3) .env example file present (.env.example/.env.sample/.env.template/.env.dist); (4) one-command bootstrap present (Makefile, justfile, Taskfile, docker-compose.yml, setup.sh, or package.json with setup/bootstrap/dev script). value = present_count/4 (0..1). Ramp-time (median first-commit to first-merge) is intentionally not measured — it overlaps lead-time/review-rework metrics and is not always available. Band thresholds are AWOS heuristics (no published DX Core 4 numeric targets): good ≥0.75, watch ≥0.50, concerning <0.50. · computed · DX Core 4 (GetDX) — Time to 10th PR (2025-09) — [DX Core 4 (GetDX) — Time to 10th PR 2025-09](https://getdx.com/blog/software-development-metrics/) |

`*` lower-bound measurement (reliability tag: `minimal`).

## Dimension: Prevention Coverage

> Whether the good state the other dimensions measure is protected against regression — per failure-mode cluster, is there a mechanical enforcement gate (pre-commit/CI/hook/scanner) or at least an agent-visible written rule?

**Score:** 25 pts (coverage 62.5% rel. today's standard)

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | PRV-01 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 1 | A secret-scanning gate (gitleaks, trufflehog, detect-secrets, git-secrets, ggshield, secretlint) runs in pre-commit or CI, mechanically blocking committed credentials · detected · NIST SSDF (SP 800-218) (2022-02) — [NIST SSDF (SP 800-218) 2022-02](https://csrc.nist.gov/pubs/sp/800/218/final) |
| 2 | PRV-11 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state an explicit secrets-handling rule, so AI agents know never to hardcode or commit credentials · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 3 | PRV-02 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 2 | Dependency risk is mechanically managed — a vulnerability scanner runs in CI (npm/yarn/pnpm audit, pip-audit, safety, snyk, trivy, grype, osv-scanner) or an update bot (Dependabot, Renovate) is configured · detected · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |
| 4 | PRV-12 | judgment | 2 | 2 | PASS | maximal (MED) | 90% | — | Agent instruction files state an explicit dependency policy, so AI agents know the rules for adding, pinning, and updating packages · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 5 | PRV-03 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Static application-security testing runs in CI or pre-commit (Semgrep, CodeQL, Bandit, Brakeman, gosec, eslint-plugin-security, Sonar), mechanically catching insecure patterns before merge · detected · NIST SSDF (SP 800-218) (2022-02) — [NIST SSDF (SP 800-218) 2022-02](https://csrc.nist.gov/pubs/sp/800/218/final) |
| 6 | PRV-13 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state the project's application-security conventions, so AI agents apply them instead of rediscovering them through review · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 7 | PRV-04 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | A linter or formatter is gated — invoked from pre-commit/husky/lint-staged or run as a CI check step — not merely configured in the repo · detected · Martin Fowler CI/CD (2024-01-18) — [Martin Fowler CI/CD 2024-01-18](https://martinfowler.com/articles/continuousIntegration.html) |
| 8 | PRV-14 | judgment | 2 | 2 | PASS | maximal (MED) | 90% | — | Agent instruction files state the project's style and formatting rules, so AI-generated code conforms without review churn · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 9 | PRV-05 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Module-boundary rules are mechanically checked (dependency-cruiser, import-linter, ArchUnit, eslint-plugin-boundaries, Nx enforce-module-boundaries) and invoked from a gate (pre-commit or CI) · detected · Martin Fowler — Software Architecture Guide (2019) — [Martin Fowler — Software Architecture Guide 2019](https://martinfowler.com/architecture/) |
| 10 | PRV-15 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state the project's architecture boundaries, so AI agents place code correctly and respect layering · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 11 | PRV-06 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | CI runs the test suite on every change and enforces a coverage threshold (e.g. --cov-fail-under, coverageThreshold, JaCoCo verification), so untested code cannot merge silently. Google's published coverage guidance bands thresholds at 60% acceptable / 75% commendable / 90% exemplary; this check grades the gate's presence only — the threshold value itself is not scored (AWOS heuristic; the detector does not parse the number) · detected · Google Testing Blog — Code Coverage Best Practices (2020-08-07) — [Google Testing Blog — Code Coverage Best Practices 2020-08-07](https://testing.googleblog.com/2020/08/code-coverage-best-practices.html) |
| 12 | PRV-16 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state the project's testing expectations, so AI agents ship tests with changes instead of leaving coverage to reviewers · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 13 | PRV-07 | detected | 3 | 3 | PASS | maximal (HIGH) | 100% | 1 | Agent configuration files and sensitive paths are mechanically guarded — hooks restrict agent access to secrets/config, or CI checks agent instruction files for integrity · detected · OWASP Top 10 for LLM Applications (2025) — [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) |
| 14 | PRV-17 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state rules protecting the agent configuration surface itself, so AI agents treat prompts, hooks, and MCP config as security-sensitive · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |
| 15 | PRV-08 | detected | 0 | 3 | FAIL | maximal (HIGH) | 100% | 0 | Documentation is mechanically checked — a link checker, markdown lint, or strict docs build (lychee, markdown-link-check, linkinator, markdownlint, vale, mkdocs --strict) runs in CI or pre-commit · detected · Diátaxis — quality (completeness) (2024) — [Diátaxis — quality (completeness) 2024](https://diataxis.fr/quality/) |
| 16 | PRV-18 | judgment | 2 | 2 | PASS | maximal (MED) | 95% | — | Agent instruction files state a docs-maintenance rule, so AI agents keep documentation aligned with the code they change · judgment · Anthropic — Claude Code best practices (2026-06) — [Anthropic — Claude Code best practices 2026-06](https://code.claude.com/docs/en/best-practices) |

## Dimension: Descriptors

> Informational size and activity signals — contributors, churn, complexity, scale, and dependency counts. Reported for context only; carries no weight in the audit total, because bigger or smaller is not better or worse.

**Informational** — descriptors reported for context, not scored toward the audit total.

| # | Check ID | Method | Weight Awarded | Weight Max | Status | Reliability | Confidence | Value | Hint |
| - | -------- | ------ | -------------- | ---------- | ------ | ----------- | ---------- | ----- | ---- |
| 1 | DESC-01 | computed | 0 | 0 | INFO | not-reliable (HIGH) | 100% | 2 | Active contributor count over the audit window ([meta].max_lookback_days): authors excluded only when both their merge-share and LOC-share fall below the active_contributor_threshold (default 5 %), filtering out drive-by reviewers and non-code participants (aggregate only, no PII) · computed · DX Core 4 (whitepaper) (2024-12) — [DX Core 4 (whitepaper) 2024-12](https://getdx.com/uploads/dx-core-4.pdf) |
| 2 | DESC-02 | computed | 0 | 0 | INFO | minimal (HIGH) * | 100% | 0.03 | Windowed code-turnover: share of recently-authored lines (within the rework horizon) that are deleted again inside the audit window — a bounded, directional rework signal (lower is better). · computed · DX Core 4 (2024-12) — [DX Core 4 2024-12](https://getdx.com/research/measuring-developer-productivity-with-the-dx-core-4/) |
| 3 | DESC-03 | computed | 0 | 0 | INFO | not-reliable (HIGH) | 100% | avg_ccn=1.34, max_ccn=34, hotspot_count=15, functions_analysed=1998, files_analysed=269, files_skipped=0, band=elite | Average and maximum cyclomatic complexity (CCN) per function, plus high-complexity hotspot count above threshold. Computed via AST decision-point counting for all bundled languages; files in unbundled languages are skipped. · computed · McCabe 1976 (1976-07) — [McCabe 1976 1976-07](https://doi.org/10.1109/TSE.1976.233837) |
| 4 | DESC-04 | computed | 0 | 0 | INFO | not-reliable (HIGH) | 100% | total_loc=22715, file_count=269 | Total source-file line count (LOC), per-language breakdown, and file count; a scale signal for contextualising other metrics · computed · Wikipedia — Source lines of code (physical SLOC) (2025) — [Wikipedia — Source lines of code (physical SLOC) 2025](https://en.wikipedia.org/wiki/Source_lines_of_code) |
| 5 | DESC-05 | computed | 0 | 0 | SKIP | — | — | missing sources: scale | Direct-dependency count from package manifests (package.json / pyproject.toml / go.mod / Cargo.toml / requirements.txt) · computed · OWASP SCVS (2020) — [OWASP SCVS 2020](https://owasp.org/www-project-software-component-verification-standard/) |

`*` lower-bound measurement (reliability tag: `minimal`).

## Connections & Sources

**Connected:**

- git history (limited history ~29 days) — trunk: origin/chore/tech-debt-cleanup (upstream of checked-out chore/tech-debt-cleanup, local +0/-0)
- GitHub Actions via gh
- GitHub PRs via gh

**Missed / limited:**

- issue tracker — no tracker connector provided; supply a Jira/Linear/GitHub Issues connector to enable work-mix and throughput metrics
- docs/wiki — no docs connector provided; supply a Confluence/Notion/GitBook connector to enable documentation coverage metrics
- incident source — no incident source connected; supply a PagerDuty/OpsGenie/incident.io, Statuspage, or code-host incident-label connector to measure MTTR from real incidents

## Linked Repositories

**MCP servers:**

- awos-recruitment (via .mcp.json)
- github-mcp-server (via .mcp.json)
- playwright (via .mcp.json)


## Tech Stack

**Languages:** JavaScript — 6 .js files, Python — 2 .py files, Java — 261 .java files · pom.xml

**Agent Tools:** Claude Code — CLAUDE.md

**CI:** GitHub Actions — .github/workflows

**Frameworks:** Spring Boot — imported in source, AWOS — context/ + .awos/spec layout


---

_Generated by AWOS ai-readiness-audit v2.4.6_
