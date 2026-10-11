# api-client

The only code that talks to the lab's JSON API. Checks never use RestAssured themselves; they call this module.

The layers, from a check down to HTTP: **check → service → api → RestAssured**.

| Package | What is in it |
| --- | --- |
| `com.labflow.client.service` | Business verbs for checks. Each comes in two forms: a plain method that returns a domain object and fails loudly on an unexpected status, and an `attempt…` twin that returns the raw answer, for checks where a refusal is the expected result. |
| `com.labflow.client.api` | One method per lab endpoint. No assertions. |
| `com.labflow.client.http` | Request setup, the answer wrapper (`LabResponse`), and the run log: `RequestLogFilter` writes one line per request and `LogMasker` hides names and passwords. |
| `com.labflow.client.assertions` | AssertJ assertions for refusals, such as `isRejectedAs(kind, message)`. |
| `com.labflow.client.preflight` | `LabPreflight`: the check, made once before a run, that the lab answers and accepts the access key. |

`LabClient` is the entry point that hands out the services.

**Run its tests** from the repository root. They need no lab (HTTP is answered by a small local stub):

```bash
./mvnw -pl api-client -am test
```

This module also holds `CiWorkflowGuardTest`, which pins the decisions in `.github/workflows/ci.yml`. Settings and the run log are explained in the [root README](../README.md).
