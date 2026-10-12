# ui-tests

The screen checks: they drive the lab's screens in a real browser with Playwright, through Page Objects.

| Location | What is in it |
| --- | --- |
| `src/main` · `com.labflow.ui.pages` | One Page Object per screen: `LoginPage`, `SampleListPage`, `CreateSamplePage`, `SampleDetailsPage`, `ReportPage`. Only these classes know locators and screen addresses. They never assert: JUnit and AssertJ are test-scoped, so the compiler enforces it. |
| `src/main` · `com.labflow.ui.config` | The browser choice and the sign-in credentials (the password prints as `***`). |
| `src/main` · `com.labflow.ui.recording` | Pauses Playwright's trace while the password is typed, so it never lands in a trace. |
| `src/test` · `…tests.happy`, `…tests.rejection` | The checks, written as user workflows. |
| `src/test` · `…tests.support` | `UiTest` (the base class; it carries the `ui` and `regression` labels) and `BrowserExtension`, which gives each check a fresh browser window and keeps a screenshot and a trace when a check fails. `AllureEvidence` attaches those two files to the failed check in the visual report; a passing check attaches nothing. |
| `src/test` · `…tests.guard` | Guard tests that keep the rules: no fixed sleeps, no locators outside Page Objects, no assertions inside them. |

A check prepares its data through `api-client` (the lab has no patient screen), then acts on the screens.

**Run the checks** from the repository root. They need a running lab and a one-time browser install:

```bash
./mvnw -N exec:java                                         # once: install Chromium
./mvnw -pl ui-tests -am test                                # every screen check
./mvnw -pl ui-tests -am test -Dheaded=true -DslowMo=250     # watch a run
```

Failure evidence lands in `target/ui-failures/<browser>/<TestClass>/<scenario>/`. WebKit, labels, opening a trace and building the visual report are explained in the [root README](../README.md).
