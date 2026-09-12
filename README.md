# Lab Workflow SDET Automation Framework

A small, production-minded Java test automation framework built as a **reference project**. It simulates a simplified, regulated laboratory workflow — Patient → Order → Sample → Processing → Report — and demonstrates REST API automation, UI automation with Page Object Model, parallel test execution, test-data isolation, and CI/CD.

> This is a practice/portfolio project. It does not reproduce any real laboratory's architecture or business logic — see `context/project-brief.md` for the original brief.

## Status

🚧 In planning. Product definition, roadmap, and architecture are defined (see below); implementation has not started yet.

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
├── ui-tests/         # Page Object Model + UI test suites
├── test-data/        # Test-data builders/factories
└── .github/workflows/tests.yml
```

## Running Tests

Once scaffolded, tests will run via:

```bash
mvn clean test
mvn -pl api-tests test
mvn -pl ui-tests test
```

Configuration (base URL, browser, environment) will be supplied via environment variables or Maven system properties — no environment values are hardcoded.
