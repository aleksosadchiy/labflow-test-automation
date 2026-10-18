# LabFlow Test Automation — Project Brief

## Context

The system under test models regulated end-to-end laboratory solutions for:

- laboratory accessioning
- sample processing
- reporting
- NIPT (Non-Invasive Prenatal Testing)
- Oncology test offerings

This is a **practice project**, not an attempt to reproduce any real laboratory vendor's internal architecture or business logic.

---

# 1. Goal

Build a small but production-minded **Java test automation framework** demonstrating:

- UI automation
- Page Object Model
- REST API automation
- Retrofit / OkHttp or RestAssured
- JUnit 5
- parallel test execution
- test-data management and isolation
- Maven multi-module architecture
- Git / GitHub
- GitHub Actions / CI/CD
- Bash / Linux
- clean architecture
- positive and negative testing
- integration / end-to-end testing
- logging and reporting
- maintainability

The goal is **quality of engineering, not the number of tests**.

---

# 2. Domain to Simulate

Create a simplified laboratory workflow.

## Main entities

- Patient
- Order
- Sample
- Processing Job
- Test Result
- Report

## Example workflow

```text
Patient
   ↓
Order created
   ↓
Sample registered
   ↓
ACCESSIONED
   ↓
READY_FOR_PROCESSING
   ↓
PROCESSING
   ↓
SEQUENCING
   ↓
BIOINFORMATICS
   ↓
QUALITY_CHECK
   ↓
RESULT_READY
   ↓
REPORT_GENERATED
```

This is a **conceptual workflow for the project**. Do not assume it represents any real laboratory's implementation.

---

# 3. Recommended Repository Structure

Use a Maven multi-module project.

```text
labflow-test-automation/
│
├── pom.xml
├── common/
├── api-client/
├── api-tests/
├── ui-tests/
├── test-data/
├── .github/workflows/tests.yml
├── README.md
└── .gitignore
```

A simpler 3-module structure is also acceptable.

**Do not over-engineer the project.**

---

# 4. API Automation

Use either **Retrofit + OkHttp** or **RestAssured**.

## Option A — Retrofit + OkHttp

Demonstrate a clean separation:

```text
Test
 ↓
Service / API layer
 ↓
Retrofit
 ↓
OkHttp
 ↓
HTTP API
```

Example:

```java
public interface SampleApi {
    @POST("/samples")
    Call<SampleResponse> createSample(@Body CreateSampleRequest request);

    @GET("/samples/{id}")
    Call<SampleResponse> getSample(@Path("id") String id);
}
```

## Option B — RestAssured

```java
given()
    .contentType(ContentType.JSON)
    .body(request)
.when()
    .post("/samples")
.then()
    .statusCode(201)
    .body("status", equalTo("ACCESSIONED"));
```

Record **why the approach was selected**.

---

# 5. API Test Coverage

## Positive scenarios

- Create and retrieve a patient
- Create an order for an existing patient
- Register and retrieve a sample
- Verify the initial sample status
- Start and complete processing
- Generate and retrieve a report

## Negative scenarios

At minimum:

- missing mandatory field
- invalid patient ID
- duplicate sample
- invalid sample type
- non-existing sample
- unauthorized request
- invalid status transition
- malformed request body
- unsupported HTTP method

Example:

```text
ACCESSIONED → PROCESSING       valid
PROCESSING → RESULT_READY      invalid
RESULT_READY → ACCESSIONED     invalid
```

---

# 6. Assertions

Do not only check HTTP status codes.

Validate:

- status code
- response body
- business fields
- headers
- error messages
- response schema where appropriate
- state transitions

Example:

```java
assertEquals(201, response.statusCode());
assertEquals("ACCESSIONED", response.status());
assertNotNull(response.sampleId());
```

---

# 7. API Client Architecture

Use separation of concerns:

```text
Tests
  ↓
Business / API layer
  ↓
API clients
  ↓
HTTP library
```

Example:

```text
SampleTests
    ↓
SampleService
    ↓
SampleApi
    ↓
Retrofit / OkHttp
```

Avoid putting raw HTTP implementation into every test.

---

# 8. UI Automation

Use a small local/public test application.

Implement **Page Object Model**.

Suggested pages:

```text
LoginPage
SampleListPage
CreateSamplePage
SampleDetailsPage
ReportPage
```

Example:

```java
public class LoginPage {
    public LoginPage enterUsername(String username) {
        // ...
        return this;
    }

    public LoginPage enterPassword(String password) {
        // ...
        return this;
    }

    public SampleListPage login() {
        // ...
        return new SampleListPage();
    }
}
```

Tests should read like business scenarios rather than locator collections.

---

# 9. Page Object Model — Principles

Definition:

> Page Object Model is a design pattern where each page or meaningful UI component is represented by a class that encapsulates its elements and user actions.

Benefits:

- maintainability
- reusability
- readability
- reduced duplication
- easier locator changes

Avoid excessive business logic and assertions inside Page Objects.

---

# 10. JUnit 5

Use:

- `@Test`
- `@BeforeEach`
- `@AfterEach`
- `@BeforeAll`
- `@ParameterizedTest`
- assertions
- tags
- extensions where useful

Example:

```java
@ParameterizedTest
@ValueSource(strings = {"", "INVALID"})
void shouldRejectInvalidPatientId(String patientId) {
    // ...
}
```

Useful tags:

```text
@Tag("api")
@Tag("ui")
@Tag("smoke")
@Tag("regression")
```

---

# 11. Parallel Execution

Configure JUnit 5 to run independent tests in parallel.

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

The framework must be **thread-safe**.

Avoid shared mutable state such as:

```java
static String currentSampleId;
```

Prefer isolated test data:

```text
Test A → SAMPLE-A-123
Test B → SAMPLE-B-456
Test C → SAMPLE-C-789
```

Risks the design must address:

- shared mutable state
- race conditions
- test-data collisions
- database conflicts
- non-thread-safe clients
- environment limitations
- order-dependent tests

---

# 12. Test Data Strategy

Create reusable test-data builders/factories.

Example:

```java
Patient patient = PatientBuilder
    .aPatient()
    .withRandomId()
    .withValidData()
    .build();
```

Generate unique data where necessary:

```java
String sampleId = "SAMPLE-" + UUID.randomUUID();
```

Think about:

```text
Create data
   ↓
Use data
   ↓
Verify
   ↓
Cleanup
```

For a regulated system, test-data traceability and reproducibility matter.

---

# 13. Cucumber — Optional

Cucumber is **optional**, not mandatory.

Example:

```gherkin
Feature: Sample registration

Scenario: Register a valid sample
  Given a valid patient exists
  When I register a new blood sample
  Then the sample should be successfully registered
  And its status should be "ACCESSIONED"
```

Concepts:

- BDD
- Gherkin
- Feature
- Scenario
- Given / When / Then
- Step Definitions

Definition:

> Cucumber is a BDD framework that allows us to describe test scenarios in plain language using Gherkin syntax, such as Given-When-Then, and connect those steps to automated test code.

Important distinction:

```text
Cucumber       → scenario language / BDD layer
RestAssured    → API automation
Selenium       → UI automation
JUnit 5        → test execution / organization
Maven          → build / dependencies
GitHub Actions → CI/CD
```

---

# 14. CI/CD

Create a GitHub Actions pipeline.

Minimum pipeline:

```text
Checkout
   ↓
Setup Java
   ↓
Install dependencies
   ↓
Build
   ↓
Run API tests
   ↓
Run UI tests
   ↓
Publish results
```

Example commands:

```bash
mvn clean test
mvn -pl api-tests test
mvn -pl ui-tests test
```

Consider separate API and UI jobs so they can execute independently.

---

# 15. Maven

Demonstrate:

- parent POM
- child modules
- dependency management
- plugins
- profiles if useful
- Surefire
- environment configuration

Keep the distinction clear between:

```text
dependency
plugin
module
profile
```

---

# 16. Configuration

Do not hardcode environment-specific values.

Bad:

```java
String baseUrl = "http://localhost:8080";
```

Better:

```bash
mvn test -DbaseUrl=https://test.example.com
```

Or environment variables:

```text
BASE_URL
API_TOKEN
BROWSER
ENVIRONMENT
```

Never commit real credentials.

---

# 17. Bash / Linux

The framework should be runnable and debuggable from a shell, using commands such as:

```bash
pwd
ls
cd
mkdir
rm
cp
mv
grep
cat
tail
head
find
curl
ps
kill
chmod
```

And:

```bash
mvn clean test
mvn clean verify
git status
git log
git diff
curl -X GET ...
```

Document how to investigate a failing test in CI/Linux.

---

# 18. Git / GitHub

Example workflow:

```text
main
  ↑
feature/test-framework
```

Commands:

```bash
git clone
git checkout -b
git status
git add
git commit
git push
git pull
git merge
git rebase
git log
git diff
```

Working practices:

- merge vs rebase
- pull requests
- code review
- conflict resolution
- protected main branch

---

# 19. Reporting

Possible choices:

- Allure
- Surefire reports
- JUnit XML
- GitHub Actions test summaries

At minimum CI should show:

```text
Total
Passed
Failed
Skipped
Duration
```

Failures should contain enough information to investigate the problem.

---

# 20. Logging

Use a proper logging framework rather than `System.out.println`.

Log useful information:

```text
Request method
Endpoint
Correlation/test ID
Response status
Important business identifiers
Failure reason
```

Do **not** log:

```text
Passwords
Access tokens
Sensitive patient information
```

This is particularly important in healthcare-related software.

---

# 21. Test Pyramid

The suite follows the test pyramid:

```text
             E2E / UI
              /\\
             /  \\
            /    \\
       Integration
          /      \\
         /        \\
        API / Service
       /            \\
      /              \\
     Unit tests
```

A practical framework should not put everything into slow UI tests.

Prefer:

```text
Many API/service tests
Some integration tests
Fewer UI/E2E tests
```

---

# 22. Regulated Environment

Medical expertise is not required.

The engineering implications that matter:

- traceability
- reproducibility
- auditability
- documented evidence
- controlled changes
- stable regression suites
- risk-based testing
- defect tracking
- release qualification
- data integrity

How testing differs in a regulated environment:

> Passing tests alone is not enough. We also need reliable evidence, traceability, reproducibility, controlled changes, documented results and a risk-based testing approach.

---

# 23. Domain Background

These concepts matter at a high level.

## NIPT

**Non-Invasive Prenatal Testing.**

Conceptually:

```text
Patient
→ blood sample
→ laboratory processing
→ sequencing
→ analysis
→ quality checks
→ result/report
```

## Oncology testing

Conceptually:

```text
Patient
→ biological sample
→ laboratory processing
→ sequencing / genomic analysis
→ interpretation
→ report
```

## Accessioning

The process of registering and identifying a laboratory sample and associating it with the correct patient/order.

Typical information:

```text
Patient
Order
Sample ID
Sample type
Collection information
Status
```

---

# 24. Design Questions the Project Should Answer

The design and its documentation should give a clear answer to each of these.

## Architecture

1. How would you design an automation framework from scratch?
2. Why separate API clients from tests?
3. How would you structure a Maven multi-module project?
4. How would you scale the framework to thousands of tests?
5. How would you avoid duplicated code?

## API

6. RestAssured vs Retrofit — when would you choose each?
7. What is idempotency?
8. What is the difference between PUT and PATCH?
9. What are 2xx, 4xx and 5xx responses?
10. How do you test authentication?
11. How do you validate JSON?
12. How do you test negative API scenarios?
13. How would you test an asynchronous API?

## UI

14. Explain Page Object Model.
15. How do you handle unstable locators?
16. How do you avoid flaky UI tests?
17. Explicit wait vs implicit wait?
18. What should and should not be inside a Page Object?

## JUnit

19. How does JUnit 5 differ from JUnit 4?
20. How do you parameterize tests?
21. How do you run tests in parallel?
22. What problems can parallel execution cause?

## CI/CD

23. How would you design a CI pipeline?
24. What should run on every pull request?
25. What should run nightly?
26. How do you investigate a test that fails only in CI?

## Test data

27. How do you isolate tests?
28. How do you generate unique test data?
29. How do you clean up test data?
30. What happens if 100 tests run in parallel against the same database?

## Engineering practice

31. What makes an automation framework maintainable?
32. How do you decide what to automate?
33. How do you deal with flaky tests?
34. How do you measure automation quality?
35. How would you introduce automation into an existing project?
36. How would you work with developers when an API is difficult to test?
37. How do you prioritize testing when release time is limited?

---

# 25. Expected Final Project

The repository should contain:

- [ ] Maven multi-module project
- [ ] Java
- [ ] JUnit 5
- [ ] REST API automation
- [ ] Retrofit/OkHttp OR RestAssured
- [ ] UI automation
- [ ] Page Object Model
- [ ] Positive API tests
- [ ] Negative API tests
- [ ] Test-data builders/factories
- [ ] Parallel execution
- [ ] Tags
- [ ] Logging
- [ ] Test reporting
- [ ] GitHub Actions
- [ ] Bash commands/documentation
- [ ] README
- [ ] Clean Git history

---

# 26. Suggested 5-Day Build Plan

## Day 1 — Architecture + API

Build:

- Maven project
- modules
- domain models
- API client
- first API tests

Focus on architecture.

## Day 2 — API Testing

Add:

- positive cases
- negative cases
- validation
- authentication
- error handling
- test-data builders

## Day 3 — UI + POM

Add:

- Page Objects
- UI tests
- waits
- locators
- screenshots/logging on failure

## Day 4 — Reliability Features

Add:

- parallel execution
- test isolation
- tags
- reporting
- configuration
- logging

## Day 5 — CI/CD + Review

Add:

- GitHub Actions
- README
- cleanup/refactoring
- architecture review
- design walkthrough

Finish with an end-to-end walkthrough of the design.

---

# 27. What NOT to Build

Do not spend time on:

- real bioinformatics
- real genetic analysis
- complex Kubernetes infrastructure
- huge numbers of tests
- 100% code coverage
- sophisticated frontend
- production-level authentication
- complicated databases
- unnecessary Cucumber abstraction

A reader of the repository should see:

> “This is a reliable automation system that is built to be maintained.”

Not:

> “Five days went into building unnecessary infrastructure.”

---

# 28. Definition of Done

The project is ready when you can clone it on a clean machine and run:

```bash
mvn clean test
```

and its documentation explains:

1. How the framework is structured.
2. Why the modules are separated.
3. How API requests are implemented.
4. How UI automation works.
5. How Page Objects work.
6. How test data is isolated.
7. How parallel execution works.
8. How CI executes the tests.
9. How failures are investigated.
10. What would change before using the framework in production.

---

# 29. Project Summary

A two-minute summary of the project:

> “LabFlow Test Automation is a small automation framework that simulates a regulated laboratory workflow. It uses Java, Maven and JUnit 5, with a dedicated API client layer and UI Page Objects. It covers positive and negative API scenarios, with test-data isolation and parallel execution. It is integrated with GitHub Actions so tests run automatically in CI. The focus is maintainability, clear separation of concerns and reliable test execution rather than simply increasing the number of tests.”

Every important technical decision is recorded with its rationale.

---

# 30. Priority if Time Is Limited

Prioritize:

```text
1. API automation
2. JUnit 5
3. Test architecture
4. Page Object Model
5. Parallel execution
6. Test data isolation
7. Maven
8. CI/CD
9. Logging/reporting
10. Cucumber
```

**Cucumber is useful to understand, but it should not take priority over API automation, framework architecture, JUnit 5, parallel execution and CI/CD.**
