# Technical Specification: Mock Lab REST API (Lab Backend for Automated Clients)

- **Functional Specification:** `context/spec/002-mock-lab-rest-api/functional-spec.md`
- **Status:** Draft
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Add a JSON REST surface to the existing `mock-lab-app` Spring Boot module, under the `/api` path prefix, next to the Thymeleaf screens. Both surfaces keep calling the same `service` layer over the same in-memory repositories, so the lab rules and data are shared (functional spec §2.8). No new module, database, or framework dependency is needed.

Five pieces of work:

1. **Services grow** to cover patients, orders, sample queries, and a target-stage transition, without changing how the screens use them.
2. **A thin `api` controller layer** maps HTTP to those services and returns JSON.
3. **A separate access-key filter** guards `/api/*` only. The existing session interceptor keeps guarding the screens only.
4. **One error mapper** turns every failure on `/api/**` (rule violations, malformed body, unknown route, wrong verb, bad key) into the same JSON body with a stable message and status.
5. **Start-up ergonomics:** a Maven Wrapper for a one-command start and an unauthenticated `GET /health` readiness check.

Everything follows the "simple, defensible" rule in `CLAUDE.md`: no Spring Security, no Bean Validation framework, no Actuator, no Docker.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1 Architecture Changes

| Change | Detail |
|---|---|
| **URL scheme** | JSON API under `/api/...`. The screens keep `/samples`, `/samples/{id}`, `/samples/{id}/report`, so there is no route collision. Progress, result and report are **sample-scoped** (`/api/samples/{id}/progress\|result\|report`), so `architecture.md`'s top-level `/processing-jobs` and `/reports` are dropped (see §3, documentation drift). |
| **Two auth mechanisms, isolated by path** | Screens: existing `SessionAuthInterceptor`, now told to skip `/api/**` and `/health` (otherwise API calls would be redirected to `/login`). API: new `ApiKeyFilter`, registered only for `/api/*`, which never creates or reads a session. |
| **Why a servlet filter, not an interceptor, for the key** | A filter runs before Spring MVC dispatch, so a missing key gives 401 **before** any 404 (unknown route) or 405 (wrong verb) can be decided. Interceptors do not reliably run for unmatched routes. This satisfies functional spec §2.1 ("no key + non-existent record → still unauthorized"). |
| **Error mapping** | One `HandlerExceptionResolver` at highest precedence, active only for URIs starting with `/api`, returning "not handled" otherwise so screens keep their existing behavior. Chosen over `@RestControllerAdvice` because unmatched routes (404) and wrong verbs (405) have no handler method and are not reliably caught by a controller advice. A shared `ApiErrorWriter` writes the JSON for both the resolver and the filter's 401. |
| **Readiness** | New `GET /health`, outside `/api`, no key, custom (not Actuator). |
| **No new dependencies** | Jackson already ships with `spring-boot-starter-web`. |

### 2.2 Data Model Changes

No new entities and no schema (in-memory). Deltas to what exists:

| Item | Change | Notes |
|---|---|---|
| `Order` ↔ `Sample` | Relationship becomes **one-to-many** | The code already allows it (`Sample` holds `orderId`, `Order` holds no sample reference). `technical-considerations.md` of spec 001 says 1-to-1: that document is stale on this point. |
| `SampleRepository` | Add `findByOrderId(orderId)` | Needed for the duplicate-type rule and for listing an order's samples. |
| `SampleStatus` | Add `displayName()` and `parse(String)` | `displayName()` gives "Ready for Processing" for messages. `parse` is case-insensitive and treats spaces as underscores, so `PROCESSING`, `processing` and `Ready for Processing` all resolve. |
| `PatientRepository`, `OrderRepository` | No change | Patient IDs generated as `PAT-<UUID>`, so they can never collide with the seeded `PAT-1001..1007`. |
| Source of truth for status | **The `ProcessingJob`**, as the screens already do | API reads derive `status` from the job. `Sample.status` is a mirror written after the job, so reading the mirror could briefly disagree. |

### 2.3 API Contracts

**Conventions:** JSON in and out; `Authorization: Bearer <key>` on every `/api/**` request; create operations return **201** with a `Location` header; enum values on the wire are **enum names** (`ACCESSIONED`, `BLOOD`), matching the screens and the assignment's example; timestamps are ISO-8601 strings; unknown request fields are ignored (so a client-supplied `id` is silently ignored, consistent with "the lab assigns IDs"); trailing slashes are not matched (`/api/samples/` → 404 "Resource not found.").

| Method & Path | Purpose | Success | Rejections (status: message) |
|---|---|---|---|
| `GET /health` | Readiness (no key) | 200 `{"status":"UP"}` | 503 `{"status":"STARTING"}` until startup, including data seeding, is complete |
| `POST /api/patients` | Register patient | 201 PatientResponse | 400 Patient name is required. |
| `GET /api/patients/{id}` | Look up patient | 200 PatientResponse | 404 Patient not found. |
| `POST /api/orders` | Place order | 201 OrderResponse | 400 Patient is required. · 404 Patient not found. |
| `GET /api/orders/{id}` | Look up order (lists its samples) | 200 OrderResponse | 404 Order not found. |
| `POST /api/samples` | Register sample | 201 SampleCreatedResponse (status `ACCESSIONED`, includes the order) | 400 Patient is required. / Sample type is required. / Invalid sample type. · 404 Patient not found. / Order not found. · 409 Order does not belong to this patient. / A sample of this type already exists for this order. |
| `GET /api/samples` | List all samples | 200 bare array (`[]` when none), ordered by creation time then ID | none beyond the common ones |
| `GET /api/samples/{id}` | Look up sample | 200 SampleResponse | 404 Sample not found. |
| `PUT /api/samples/{id}/status` | Move to a requested stage | 200 SampleResponse | 400 Status is required. / Invalid status. · 404 Sample not found. · 409 Invalid status transition: cannot move from *X* to *Y*. |
| `GET /api/samples/{id}/progress` | Stage history | 200 ProgressResponse | 404 Sample not found. |
| `GET /api/samples/{id}/result` | Test result | 200 ResultResponse | 404 Sample not found. · 409 Test result is not ready yet. |
| `GET /api/samples/{id}/report` | Report | 200 ReportResponse | 404 Sample not found. · 409 Report is not ready yet. |

**Common rejections on every `/api/**` route:**

| Status | Message | Cause |
|---|---|---|
| 401 | Unauthorized. | Missing/incorrect key (with `WWW-Authenticate: Bearer`); wins over every other rejection |
| 400 | Malformed request body. | Unreadable JSON or wrong shape |
| 404 | Resource not found. | Unknown route |
| 405 | Method not allowed. | Verb not supported by the route (`Allow` header kept) |
| 415 / 406 | Unsupported media type. / Not acceptable. | _Not in the functional spec; added so these cases still return the standard body._ |
| 500 | Internal server error. | Unexpected failure; never leaks a stack trace |

**Status-code rules (deliberate, one message ↔ one status everywhere):** "…not found." is always 404, even when the unknown ID is inside a POST body; "…not ready yet." is 409 (state conflict), distinct from 404; business-rule conflicts (wrong patient for order, duplicate type, invalid transition) are 409; missing/invalid input is 400.

**Error body (all rejections):** `{ status, error, message, path, timestamp }`. Tests assert on `status` and `message`.

**DTOs (Java records, fields only):**

| DTO | Fields |
|---|---|
| PatientRequest / PatientResponse | `name` / `id, name` |
| OrderRequest | `patientId` |
| OrderResponse | `id, patientId, createdAt, samples[{id, sampleType, status}]` |
| SampleRequest | `patientId, sampleType, orderId?` (blank `orderId` is treated as "not named") |
| SampleResponse | `id, patientId, orderId, sampleType, status, createdAt` |
| SampleCreatedResponse (POST only) | SampleResponse fields, plus `order{id, patientId, createdAt}` |
| StatusRequest | `status` |
| ProgressResponse | `sampleId, currentStatus, stages[{status, reachedAt}]` (includes the current stage) |
| ResultResponse | `sampleId, testName, resultValue` |
| ReportResponse | `sampleId, testName, resultValue, generatedAt` |
| ErrorResponse | `status, error, message, path, timestamp` |

**Access key:**
- Header `Authorization: Bearer <key>` (scheme case-insensitive; blank key invalid).
- Configured by `mock-lab.api.token: ${API_TOKEN:mock-lab-local-token}` in `application.yml`. `API_TOKEN` is the variable name already used in `architecture.md`, so app and tests share one name. Documented default is for local use only.
- The app **fails at startup** if the resolved token is blank (an empty `API_TOKEN` would otherwise silently override the default).
- Compared in constant time; never logged, never echoed in an error body; request-detail logging stays off.

### 2.4 Component Breakdown

New and changed files under `mock-lab-app/src/main/java/com/mocklab/`:

```
api/                                  # replaces the .gitkeep placeholder
  PatientApiController.java           # /api/patients
  OrderApiController.java             # /api/orders
  SampleApiController.java            # /api/samples, /status, /progress, /result, /report
  dto/                                # request/response records (table above)
  error/
    ApiErrorResolver.java             # HandlerExceptionResolver for /api/** only
    ApiErrorWriter.java               # shared JSON writer (resolver + filter)
    ApiErrorResponse.java             # error body
    LabMessages.java                  # every user-facing message as a constant

config/
  ApiKeyFilter.java                   # Bearer check, /api/* only
  ApiSecurityConfig.java              # filter registration, token property, fail-fast blank check
  WebConfig.java                      # CHANGED: exclude /api/** and /health from the session interceptor

web/HealthController.java             # GET /health (uses the framework's readiness state)

exception/                            # replaces the .gitkeep placeholder
  LabRuleException.java               # carries FailureKind + message
  FailureKind.java                    # VALIDATION | NOT_FOUND | CONFLICT

service/
  PatientService.java                 # NEW: register, find
  OrderService.java                   # NEW: place, find (with its samples)
  SampleQueryService.java             # NEW: get, list, progress, result, report + "not ready" rules
  SampleService.java                  # CHANGED: orderId overload, new rules, lock
  SampleCreationResult.java           # CHANGED: Failure gains a FailureKind
  ProcessingJobService.java           # CHANGED: advanceTo(sampleId, target)

domain/SampleStatus.java              # CHANGED: displayName(), parse()
repository/SampleRepository.java      # CHANGED: findByOrderId()
```

Outside the Java tree: `mock-lab-app/src/main/resources/application.yml` (token property), `mock-lab-app/pom.xml` (`<finalName>mock-lab-app</finalName>` so the jar matches `architecture.md`), `mock-lab-app/mvnw` and `mock-lab-app/.mvn/` (Maven Wrapper), and a README "Running the lab" section.

### 2.5 Logic / Algorithm

**Sample registration precedence** (first failure wins). Rules 1–4 are unchanged from spec 001; 5–7 are new and only apply when an `orderId` is supplied:
1. patient blank → *Patient is required.* (400)
2. type blank → *Sample type is required.* (400)
3. patient unknown → *Patient not found.* (404)
4. type not supported → *Invalid sample type.* (400)
5. order unknown → *Order not found.* (404)
6. order belongs to another patient → *Order does not belong to this patient.* (409)
7. order already has a sample of this type → *A sample of this type already exists for this order.* (409)
8. otherwise create (new order if none named), sample `ACCESSIONED`, and processing job.

The existing two-argument `createSample` (used by the screen) delegates with no order and behaves exactly as today. `Failure` gains a `FailureKind` so the API can pick 400 vs 404 vs 409 while `message()` stays as-is.

**Target-stage change (`advanceTo`):** validation order is (1) status missing → 400, (2) status not a known stage → 400, (3) sample lookup → 404, (4) rule: the only accepted target is the stage immediately after the current one; anything else (skip, backward, same stage, or any move from *Report Generated*) → 409 with display names, e.g. "Invalid status transition: cannot move from Processing to Result Ready." On success it reuses the same private step that `advance()` uses, so the result/report side effects (created on entering *Result Ready* / *Report Generated*) live in one place and fire exactly once. `advance()` (the screens' button, which no-ops at the final stage) is untouched.

**Idempotency contrast (design note):** `PUT …/status` targets a *desired state*, so retrying it cannot move the sample further (a retry gets 409 rather than double-advancing). The screens' `POST …/advance` means "do the next step", so retrying it advances twice.

**PUT vs PATCH (design note):** PUT replaces the whole addressed resource; PATCH applies a partial change. `/status` is a one-field sub-resource, so PUT is exact; `PATCH /samples/{id}` would invite partial bodies (e.g. changing the type) that would then have to be rejected.

**Concurrency:** the whole `createSample` body becomes `synchronized` (the "one sample per type per order" rule is check-then-act), and `advanceTo` shares the existing monitor with `advance`. The two services never hold each other's lock, so there is no deadlock risk. Global locks are fine at this scale and satisfy "different samples don't interfere" (spec §2.8).

**Error style:** new services and `advanceTo` throw `LabRuleException(kind, message)`; `createSample` keeps its sealed result type (the screens depend on it) and the API controller converts `Failure` to `LabRuleException`. Two styles, but it avoids rippling through the screens and their 46 existing tests.

**Start-up and CI usage** (no Docker):
- Local: `cd mock-lab-app && ./mvnw spring-boot:run` (only a JDK 17+ needed).
- Jar / CI: `./mvnw -q package -DskipTests` then `java -jar target/mock-lab-app.jar &`.
- Readiness poll: `curl -sf --retry 30 --retry-delay 1 --retry-max-time 30 --retry-connrefused http://localhost:8080/health` (curl retries on the 503).
- `GET /health` returns 200 only once the framework's readiness state is "accepting traffic", which happens **after** the data seeder has run. The port opens earlier, so `/login` is not a valid readiness signal.
- "Within 30 seconds" is measured from launching the jar to the first successful `/health`, excluding the build.

---

## 3. Impact and Risk Analysis

**System Dependencies**
- Reuses the services and repositories from spec 001; the screens and their tests must keep working unchanged (the 46 existing tests are a gate).
- Later phases depend on this contract: `api-client`/`api-tests` (Phase 1) will bake in the paths, statuses, and messages above.
- Documentation drift to fix later (not edited here): `architecture.md` (drop `/processing-jobs` and `/reports` as top-level paths, add `/api` prefix, `/health`, `mock-lab-app.jar`, Maven Wrapper; via `/awos:architecture`); spec 001's technical doc (Order is now 1-to-many; the "duplicate sample ID becomes reachable via the API" note no longer applies, since IDs are lab-assigned).

**Potential Risks & Mitigations**

| Risk | Mitigation |
|---|---|
| Status codes or messages drift and silently break later API tests | Every message lives in one `LabMessages` constants class; tests assert on the literal strings so any edit fails loudly. |
| Race on "one sample per type per order" under parallel test runs | Lock in `createSample`; a test fires N concurrent registrations for the same order and type and expects exactly one 201. |
| The two auth mechanisms bleed into each other | Path-prefix separation. Tests: a session cookie on `/api` → 401; a Bearer key on a screen route → still 302. |
| 401-before-404 ordering breaks | The filter runs before dispatch. Tests: no key + missing sample, unknown route, and DELETE all give 401. |
| The error resolver does not catch 404/405/malformed body as intended (not verifiable without running it) | The first acceptance test must prove all four cases (malformed body, wrong verb, unknown route, rule violation) return the standard body. |
| Blank `API_TOKEN` disables auth silently | Fail fast at startup. |
| In-memory data resets only on restart | No reset endpoint (per spec 001). Tests must use their own unique data and never assume an empty list. |
| Jackson leniency: unknown fields are ignored; a JSON number for `patientId` may be coerced to a string | Documented in §2.3. A test decides the coercion behavior; if it is wrong, tighten the mapper rather than the tests. |
| Read/write tearing (job saved, then mirrored sample) | API reads use the job status. |
| Logging sensitive data | Never log the `Authorization` header, the token, or patient names. |
| **The assignment's own example lists `ACCESSIONED → PROCESSING` as a valid transition**, but its workflow diagram (and this spec) put *Ready for Processing* in between | The functional spec wins: only the next stage is accepted, so that example is rejected here. Called out so Phase 1 test authors and the explanation don't trip on it. |

**Points where the functional spec is silent, resolved here as assumptions:**
- **Assumption:** wire values are enum names (`ACCESSIONED`), while messages use display names ("Processing").
- **Assumption:** stage-change precedence is 400 (input) → 404 (lookup) → 409 (rule) when several problems overlap.
- **Assumption:** a blank order ID means "no order named"; an order response lists its samples (implied by "both appear under that order").
- **Assumption:** patient names may repeat, are trimmed, and have no length limit.
- **Assumption:** report date is returned as the full ISO-8601 instant, as the screen does.
- **Assumption:** "does not report ready before startup finished" (spec §2.9) is verifiable only in a unit test with a mocked readiness state, because the port opens before the seeder runs.

---

## 4. Testing Strategy

This covers the mock app's own correctness only; the Phase 1 `api-tests` module is out of scope.

- **Unit tests (JUnit 5 + AssertJ, no Spring context):**
  - `ProcessingJobService`: a parameterized table over every (current, requested) pair proves only the next stage is accepted, messages use display names, same-stage/backward/from-final give 409, and side effects fire exactly once. Existing `advance()` tests stay untouched.
  - `SampleService`: the three new order rules, the full precedence order, `FailureKind` per failure, and a multi-threaded duplicate-type test.
  - New: `PatientService`, `OrderService`, `SampleQueryService` ("not ready" rules), `SampleStatus.parse`/`displayName`.
- **Acceptance tests (`@SpringBootTest` + MockMvc), one class per functional-spec section (2.1–2.8)** in `src/test/java/com/mocklab/api/`, tagged `// @layer: integration` and `// @spec: 002-mock-lab-rest-api` like the existing ones:
  - New `AbstractApiAcceptanceTest` with an `Authorization` helper and its own small sign-in helper (the existing base class is package-private and the cross-surface tests in §2.8 need a screen session).
  - Do **not** use `@DirtiesContext` per test on the API side: one shared context per class, with UUID-based data, mirroring the isolation the project teaches. Only tests that assert global state (the empty-list test, the concurrency test that checks list size) get a fresh context.
  - Cross-surface tests: register via API then see it on the Sample List screen; register via the Create Sample screen then fetch via API.
  - Auth and error tests: session cookie on `/api` → 401; Bearer key on a screen → 302; the four error-mapper cases above.
- **Readiness:** a `@SpringBootTest(RANDOM_PORT)` test that `GET /health` returns 200 with no key, plus a slice test with a mocked readiness state returning 503 when not ready. The real `java -jar` + `curl` poll is documented here and wired up in the CI/CD spec.
- **Regression gate:** all 46 existing tests must still pass unchanged.
- **Manual pre-completion check** (cited as evidence, as in spec 001): start the jar with the Maven Wrapper, then run one full curl walk-through: create patient → order → sample → seven `PUT …/status` calls → result → report, plus each documented rejection.
