# api-tests

The workflow checks: they drive the lab through its JSON API and read like business statements. There is no production code here, only tests.

| Package | What is in it |
| --- | --- |
| `com.labflow.tests.happy` | The successful paths: patient, order, sample, every stage, the report. |
| `com.labflow.tests.rejection` | The refusals: missing fields, unknown records, duplicates, a skipped stage, a missing or wrong access key, a malformed body. |
| `com.labflow.tests.support` | `LabTest` (the base class every check extends; it carries the `api` and `regression` labels), the pre-flight listener, and `AllureStepAppender`, which turns each request line of the run log into a step of that check in the visual report (the same safe line: no headers, names or bodies). |
| `com.labflow.tests` | Guard tests that read the source as text and keep the rules: no raw HTTP in a check, no hard-coded settings, no console printing, data only from `test-data`. |

A check gets its data from `test-data` and talks to the lab only through the services in `api-client`.

**Run the checks** from the repository root. They need a running lab (see [Running the lab](../README.md#running-the-lab)):

```bash
./mvnw -pl api-tests -am test                  # every workflow check
./mvnw -pl api-tests -am test -Dgroups=api     # without the guard tests
```

If the lab is not running, the run stops before the first check with one line starting `LAB PRE-FLIGHT FAILED:`. Labels, settings, the run log and building the visual report are explained in the [root README](../README.md).
