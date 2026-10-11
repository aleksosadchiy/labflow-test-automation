# mock-lab-app

The Mock Lab: the system under test. A small Spring Boot application with in-memory data, a JSON API and five server-rendered screens. It is a separate Maven build with its own wrapper, on purpose: no command in the test framework can build, start or stop it.

| Package | What is in it |
| --- | --- |
| `com.mocklab.api` | The JSON API under `/api` (patients, orders, samples, stage changes, progress, result, report), its request and answer records in `api.dto`, and the error mapping in `api.error`. |
| `com.mocklab.web` | The Thymeleaf screens (sign-in, sample list, create sample, sample details, report) and the `/health` readiness endpoint. |
| `com.mocklab.service` | The lab rules, shared by the API and the screens, including the stage order from Accessioned to Report Generated. |
| `com.mocklab.domain`, `com.mocklab.repository` | The records and their in-memory stores. |
| `com.mocklab.config` | Access control (`ApiKeyFilter` for `/api/*`, `SessionAuthInterceptor` for the screens) and the seed data (`DataSeeder`). |

**Build and run** from this directory. Only a JDK 17+ is needed:

```bash
./mvnw spring-boot:run        # start on http://localhost:8080
./mvnw test                   # the lab's own tests (no browser, no running lab needed)
./mvnw spotless:apply         # fix formatting
```

The lab is ready when `GET /health` answers `200`. Settings come from the environment: `API_TOKEN` (the API's access key), `MOCK_LAB_USERNAME` and `MOCK_LAB_PASSWORD` (the screen sign-in). Each has a documented local default in `src/main/resources/application.yml`.

Seeded patients, the readiness check and the jar form used by CI are described in the [root README](../README.md#running-the-lab).
