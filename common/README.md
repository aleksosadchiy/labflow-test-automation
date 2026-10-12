# common

What every other framework module shares. It has no HTTP code and depends on nothing in this project.

| Package | What is in it |
| --- | --- |
| `com.labflow.common.model` | The lab's concepts as plain records and enums: patient, order, sample, workflow stage, sample type. |
| `com.labflow.common.config` | `LabConfig` (lab address, access key, environment) and the rule for where each setting comes from. The access key is wrapped in a type that prints as `***`. |
| `com.labflow.common.junit` | JUnit 5 support used by both test modules: the labels (`@ApiCheck`, `@UiCheck`, `@Smoke`, `@Regression`), `ChecksAtOnceStrategy` (how many checks run at once), `CheckNameInterceptor` (the check's name on every log line), `CheckCountListener` (the `Lab checks run: N` line) and `LabChecksAllureListener`, which passes only the checks labelled `api` or `ui` on to the visual report, so the framework's own self-checks stay out of it. |

**Run its tests** from the repository root. They need no lab:

```bash
./mvnw -pl common test
```

Settings, labels, running checks side by side and building the visual report are explained in the [root README](../README.md).
