# ui-tests

The screen checks: they drive the lab's screens in a real browser with Playwright, through Page Objects.

| Location | What is in it |
| --- | --- |
| `src/main` · `com.labflow.ui.pages` | One Page Object per screen: `LoginPage`, `SampleListPage`, `CreateSamplePage`, `SampleDetailsPage`, `ReportPage`. Only these classes know locators and screen addresses. They never assert: JUnit and AssertJ are test-scoped, so the compiler enforces it. |
| `src/main` · `com.labflow.ui.config` | The browser choice and the sign-in credentials (the password prints as `***`). |
| `src/main` · `com.labflow.ui.recording` | Pauses Playwright's trace while the password is typed, so it never lands in a trace. |
| `src/test/features` | The scenario files, one per topic: `01-getting-access.feature`, `04-…`, `05-…`, `06-…`, `07-…` and `09-the-whole-journey.feature`, 16 scenario entries in all. Their tags (`@ui`, `@regression`, `@accepted` or `@refused`, `@smoke` on three) are the labels `-Dgroups` selects by. |
| `src/test` · `com.labflow.ui.tests` | `ScreenScenariosTest`, the runner: a JUnit suite that runs the scenario files through the Cucumber engine, so Maven counts every scenario. |
| `src/test` · `…tests.steps` | The code behind the scenario lines: one class per scenario file, named after it (`GettingAccessSteps`, `RegisteringASampleSteps`, … `TheWholeJourneySteps`). Steps act on the screens only through the Page Objects. |
| `src/test` · `…tests.hooks` | `UiHooks`: a fresh browser window per scenario, tracing with the sign-in pause, the 2-minute watchdog, a screenshot and a trace when a scenario fails, the scenario's name on run-log lines and the report's family, topic and sub-group. |
| `src/test` · `…tests.world` | `UiWorld`: what one scenario knows (its page, the lab's settings, the screen it is on), shared by its step classes. |
| `src/test` · `…tests.framework` | `browser.BrowserPool` (one Playwright and browser per worker thread, closed after the last scenario), `evidence` (`AllureEvidence` attaches the screenshot and trace to the failed check in the visual report; `ArtifactNames` makes safe folder names), `preflight.UiPreflightListener` and `watchdog.ScenarioWatchdog` (names and interrupts a scenario after 2 minutes). |
| `src/test` · `…tests.selfcheck` | The framework's own checks, never in the report: `guards` (no fixed sleeps, no locators outside Page Objects, no assertions inside them, every scenario line has its code, the agreed count, names and tags) and `framework` (tests of the hooks' evidence, the browser pool, the watchdog and the pre-flight). |
| `src/test` · `com.labflow.ui.config`, `…pages`, `…recording` | Tests of the `src/main` classes, in the same packages. |

A scenario prepares its data through `api-client` (the lab has no patient screen), then acts on the screens.

**Run the checks** from the repository root. They need a running lab and a one-time browser install:

```bash
./mvnw -N exec:java                                         # once: install Chromium
./mvnw -pl ui-tests -am test                                # every screen check
./mvnw -pl ui-tests -am test -Dheaded=true -DslowMo=250     # watch a run
```

Failure evidence lands in `target/ui-failures/<browser>/<topic>/<scenario>-<line>/`. A scenario still running after 2 minutes is named in the run log and interrupted; a call that ignores the interrupt ends the whole run as failed at Surefire's 10-minute fork limit. WebKit, labels, opening a trace, how the report is organised, adding a scenario and building the visual report are explained in the [root README](../README.md).
