# api-tests

The workflow checks: they drive the lab through its JSON API, and each one is a plain-language scenario (Given, When, Then) in a scenario file. There is no production code here, only tests.

| Location | What is in it |
| --- | --- |
| `src/test/features` | The scenario files, one per topic: `01-getting-access.feature` to `09-the-whole-journey.feature`, 47 scenario entries in all. Their tags `@api` and `@regression` (on the `Feature:` line), `@accepted` or `@refused`, and `@smoke` on three, are the labels `-Dgroups` selects by. |
| `com.labflow.tests` | `WorkflowScenariosTest`, the runner: a JUnit suite that runs the scenario files through the Cucumber engine, so Maven counts every scenario and a failing one fails the build. |
| `com.labflow.tests.steps` | The code behind the scenario lines: one class per scenario file, named after it (`GettingAccessSteps`, `PatientsSteps`, … `TheWholeJourneySteps`). |
| `com.labflow.tests.hooks` | `ScenarioHooks`: puts `<topic> › <scenario>` on every run-log line of a scenario, and sets the report's family, topic and sub-group. |
| `com.labflow.tests.world` | `LabWorld`: what one scenario knows (the lab client, what it created, the last answer), shared by its step classes. |
| `com.labflow.tests.framework` | `preflight.LabPreflightListener` (the check, once before a run, that the lab answers and accepts the key) and `log.AllureStepAppender`, which turns each request line of the run log into a step of that check in the visual report (the same safe line: no headers, names or bodies). |
| `com.labflow.tests.selfcheck` | The framework's own checks, never in the report. `guards` read the source and the scenario files as text and keep the rules: no raw HTTP in a step, no hard-coded settings, no console printing, every scenario line has its code (a dry run), the agreed count, names and tags. `RunLogSecrecyGuardTest` drives the lab and proves the run log never holds the access key or a patient's name. `framework` holds the tests of the pre-flight and of `AllureStepAppender`. |

A step gets its data from `test-data` and talks to the lab only through the services in `api-client`.

**Run the checks** from the repository root. They need a running lab (see [Running the lab](../README.md#running-the-lab)):

```bash
./mvnw -pl api-tests -am test                  # every workflow check
./mvnw -pl api-tests -am test -Dgroups=api     # without the self-checks
```

If the lab is not running, the run stops before the first check with one line starting `LAB PRE-FLIGHT FAILED:`. Labels, settings, the run log, how the report is organised, adding a scenario and building the visual report are explained in the [root README](../README.md).
