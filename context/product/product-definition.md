# Product Definition: Lab Workflow SDET Automation Framework

- **Version:** 1.0
- **Status:** Proposed

---

## 1. The Big Picture (The "Why")

### 1.1. Project Vision & Purpose

To build a small, production-minded Java test automation framework that demonstrates senior-level SDET engineering practices — API automation, UI automation, clean architecture, parallelism, test-data isolation, and CI/CD — by simulating a simplified, regulated laboratory workflow (patient → order → sample → processing → report), so the author is fully prepared for a technical review and can speak to every design decision with confidence.

_Source: derived directly from `context/project-brief.md`._

### 1.2. Target Audience

Primarily a single user: the author, building a reference project in a regulated laboratory domain. Secondarily, the technical reviewers who will read the code and ask about it — the framework functions as a portfolio artifact they will evaluate.

### 1.3. User Personas

- **Persona 1: "Alex, the Author"**
  - **Role:** the author, builds and rehearses the project over roughly 5 days.
  - **Goal:** Ship a small, clean framework that visibly demonstrates real-world skills (not just checkbox coverage), and be able to explain every architectural choice.
  - **Frustration:** Limited time; risk of over-engineering or under-preparing; needs to prioritize what actually gets evaluated.

- **Persona 2: "Dana, the Reviewer"** _(assumption — not stated explicitly in the source document, inferred from its review and evaluation focus)_
  - **Role:** Senior technical reviewer assessing automation engineering maturity.
  - **Goal:** Quickly judge whether the author understands framework architecture, test design, and regulated-environment testing concerns — not just whether tests pass.
  - **Frustration:** Sees many projects that write large numbers of shallow tests or over-engineered infrastructure instead of demonstrating sound judgment.

### 1.4. Success Metrics

- The repository can be cloned on a clean machine and `mvn clean test` runs successfully end-to-end.
- Every item in the assignment's "Expected Final Project" checklist is present (Maven multi-module, JUnit 5, REST API automation, UI automation with POM, positive/negative tests, test-data builders, parallel execution, tags, logging, reporting, GitHub Actions, README, clean Git history).
- The author can clearly explain all 10 points in the "Definition of Done" (architecture, module separation, API layer, UI automation, Page Objects, test-data isolation, parallel execution, CI execution, failure investigation, and production-readiness gaps) and deliver the prepared 2-minute pitch.
- Built within the suggested 5-day preparation window without unnecessary scope creep (see Out-of-Scope).

---

## 2. The Product Experience (The "What")

### 2.1. Core Features

- **REST API test automation** for the simulated lab workflow (Patient, Order, Sample, Processing Job, Test Result, Report) using either Retrofit + OkHttp or RestAssured, with a clean Test → Service/API layer → HTTP client separation.
- **Positive and negative API test coverage**, including invalid input, invalid state transitions (e.g. `PROCESSING → RESULT_READY` rejected), authorization failures, and malformed requests — asserting status codes, response bodies, headers, and business fields (not status codes alone).
- **UI automation with Page Object Model** (e.g. `LoginPage`, `SampleListPage`, `CreateSamplePage`, `SampleDetailsPage`, `ReportPage`), written so tests read as business scenarios.
- **Thread-safe, parallel JUnit 5 execution** with isolated, uniquely generated test data (builders/factories) to avoid shared state and data collisions.
- **CI/CD pipeline** (GitHub Actions) that builds the project and runs API and UI test jobs independently, publishing results.
- **Structured logging and test reporting** (e.g. Surefire/Allure/JUnit XML) that captures enough detail to investigate failures, while explicitly never logging credentials, tokens, or sensitive patient data.

### 2.2. User Journey

The author clones the repository and scaffolds a Maven multi-module project (`common`, `api-client`, `api-tests`, `ui-tests`, `test-data` or a simpler 3-module equivalent). They build domain models and an API client layer, then write positive and negative API tests against the simulated lab workflow. Next, they add UI automation using Page Objects against a small local/public test app. They layer in parallel execution, test-data isolation, tags, logging, and reporting, then wire up a GitHub Actions pipeline that runs API and UI tests as separate jobs and publishes results. Finally, they rehearse explaining the architecture and every design decision.

---

## 3. Project Boundaries

### 3.1. What's In-Scope for this Version

- A Maven multi-module Java project with clear module separation.
- A simplified, conceptual lab domain (Patient, Order, Sample, Processing Job, Test Result, Report) and workflow (Accessioned → Ready for Processing → Processing → Sequencing → Bioinformatics → Quality Check → Result Ready → Report Generated) — explicitly not a reproduction of any real laboratory system.
- REST API automation (Retrofit/OkHttp or RestAssured) with positive and negative coverage.
- UI automation via Page Object Model against a small local/public test application.
- JUnit 5 with parameterized tests, tags (`api`, `ui`, `smoke`, `regression`), and parallel execution.
- Test-data builders/factories with unique, isolated data per test.
- Externalized configuration (no hardcoded environment values; base URL, API token, browser, environment via CLI args or env vars).
- GitHub Actions CI/CD pipeline with separate API/UI jobs and published test results.
- Logging (via a real logging framework, not `System.out.println`) and test reporting.
- Documentation: README explaining structure, setup, and how to run tests; clean Git history.
- Optional: a small Cucumber/Gherkin layer for one or two scenarios, kept minimal and not prioritized over the core items above.

### 3.2. What's Out-of-Scope (Non-Goals)

- Real bioinformatics or genetic analysis logic.
- Reproducing any real laboratory's internal architecture or business logic.
- Complex infrastructure such as Kubernetes.
- Maximizing test count or chasing 100% code coverage.
- A sophisticated or polished frontend.
- Production-grade authentication/authorization.
- Complicated database setups.
- Heavy Cucumber/BDD abstraction layers beyond a minimal, optional demonstration.

---

_Note: This is a self-contained practice/reference project, not a customer-facing product. Sections above have been adapted accordingly — "target audience" and "success metrics" are framed around review readiness and reviewer evaluation rather than end-user adoption._
