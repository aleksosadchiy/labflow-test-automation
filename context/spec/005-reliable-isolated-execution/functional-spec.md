# Functional Specification: Reliable, Isolated Runs with Settings and a Run Log (Phase 2)

- **Roadmap Item:** Phase 2 — Reliable, Isolated Test Execution (labels and multi-input checks, running checks side by side, reusable test-data recipes) and Configuration & Logging (settings from outside the project, a run log)
- **Status:** In Review
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The framework already has two families of checks against the Mock Lab: **workflow checks**, which talk to the lab's connected interface (specification 003), and **screen checks**, which drive its screens in a browser (specification 004). Both run one check at a time, always in full, against one browser type, and the only record of a run is the pass/fail output.

That is fine for a demo and weak for a real team. A real team asks four questions that this framework cannot answer yet:

1. **"Can I run just the important bit?"** Before a merge, an engineer wants a quick confidence run (a **smoke** run), not the whole suite. A nightly job wants everything (a **regression** run). Today there is no way to pick.
2. **"Why does the full run take so long?"** Checks run one after another. Running several at once is the normal way to keep a growing suite fast, and it only works if checks never share or overwrite each other's data.
3. **"Can I point it at another lab or browser without editing code?"** The lab address and access key can already be set from outside. The browser and the target environment cannot.
4. **"What happened during the run that failed in the pipeline?"** When a check fails somewhere the engineer cannot watch, they need a written record of what the framework asked the lab and what the lab answered. In a healthcare-like system that record must never contain access keys, passwords or patient names.

In this specification the **engineer** is whoever runs the framework (in practice the author rehearsing, or a reviewer). The **lab** is the Mock Lab.

Success is measured by four things:

- The engineer can run a smoke subset, a regression subset, only workflow checks or only screen checks, each with one documented command.
- A full run with checks side by side passes every time across repeated runs, and takes noticeably less time than the same run one check at a time.
- The lab address, access key, browser and environment can all be changed without editing anything in the project.
- After any run, the engineer can read what each check asked the lab and what the lab answered, and the record contains no secrets or patient names.

---

## 2. Functional Requirements (The "What")

### 2.1. Labels for Picking What to Run

- **As an** engineer, **I want** every check to carry labels saying what kind of check it is, **so that** I can run just the subset I need.
  - **Acceptance Criteria:**
    - [ ] When a reader opens any check, then it is labelled either as a workflow check ("api") or as a screen check ("ui"), and also as "smoke", "regression" or both.
    - [ ] When the engineer runs the documented "smoke" command while the lab is running, then only the checks labelled "smoke" run (together with the framework's own quick self-checks, which need no lab), and every check run passes.
    - [ ] When the engineer runs the documented "regression" command, then every check labelled "regression" runs, which is all workflow and screen checks.
    - [ ] When the engineer runs the documented "api" or "ui" command, then only workflow checks or only screen checks run respectively.
    - [ ] When a reader lists the checks labelled "smoke", then they are exactly the key successful paths plus one refusal: the full successful workflow through the connected interface, a request refused for a missing or wrong access key, signing in on screen, and the one full journey through the screens to the report (roughly 5 to 8 checks in total).
    - [ ] When the engineer runs the smoke command, then it finishes in under a third of the time the full regression run takes on the same machine.
    - [ ] When the engineer asks for a label that no check carries, then the run finishes and its output clearly states that 0 checks ran, so the engineer can see that the label matched nothing.

### 2.2. One Check, Many Inputs

- **As a** reviewer, **I want** checks that differ only by their input to be written once with a table of inputs, **so that** the suite stays small and every variation is still visible.
  - **Acceptance Criteria:**
    - [ ] When a reader looks for checks that repeat the same steps with different inputs (for example, each missing required field, each invalid sample type, each blank sign-in field), then each such group is written as one check with a list of inputs, not as copied checks.
    - [ ] When the engineer reads the run output for such a check, then each input appears as its own line with a plain-language name describing that input (for example "a sample without a patient is refused"), and a failure names the exact input that failed.

### 2.3. Running Checks Side by Side

- **As an** engineer, **I want** checks to run several at a time, **so that** the full suite stays fast as it grows.
  - **Acceptance Criteria:**
    - [ ] When the engineer runs the full suite with default settings, then several checks run at the same time, for both workflow checks and screen checks, and each screen check that is running at the same time has its own separate browser window.
    - [ ] When the engineer runs the full suite with checks side by side, then it takes less time than with checks one at a time on the same machine, and the README records both measured times. No fixed speed-up is required.
    - [ ] When the engineer runs the full suite side by side three times in a row against the same running lab without restarting it, then all three runs pass.
    - [ ] When the engineer starts a run without choosing how many checks run at once, then the run uses as many as the machine has processor cores.
    - [ ] When the engineer sets the documented "checks at once" option to a number, then no more than that many checks run at the same time.
    - [ ] When the engineer sets the "checks at once" option to 1, then checks run one after another and all pass, so a confusing failure can be investigated without other checks running alongside.

### 2.4. Reusable Recipes for Test Data

- **As an** engineer writing a new check, **I want** one shared place that makes valid patients, orders and samples with sensible defaults, **so that** a check only spells out the detail it is actually about.
  - **Acceptance Criteria:**
    - [ ] When a reader opens any check that needs a patient, order or sample, then the check gets it from the shared recipes and states only the details that matter to that check (for example "a sample of an unsupported type"); everything else is filled in with valid defaults.
    - [ ] When a reader looks for where test data is made, then they find exactly one shared place used by both workflow checks and screen checks, instead of separate copies in each.
    - [ ] When any two checks run at the same time, then every patient name, order and sample they create is unique to that check, so neither can see or change the other's data.
    - [ ] When a reader sees a patient created by a check, then the name is obviously made up and marks it as created by the framework (for example it starts with a fixed test prefix), so it can never be mistaken for a real person.

### 2.5. Settings from Outside the Project

- **As an** engineer, **I want** to choose the lab address, access key, browser and environment when I start a run, **so that** the same project works on my machine, in the pipeline and against another lab without edits.
  - **Acceptance Criteria:**
    - [ ] When the engineer starts a run without any settings, then the run uses the lab's standard local address, its documented local key, the default browser and the "local" environment, and the run passes against a locally started lab.
    - [ ] When the engineer sets the browser to any of the supported browsers, then the screen checks run in that browser and pass. The supported browsers are Chromium (the default), Firefox and WebKit; each extra browser is obtained with one documented, one-time setup command.
    - [ ] When the engineer sets a browser name that is not supported, then the run stops before any check with a message naming the value given and listing the supported names.
    - [ ] When the engineer sets the environment name, then the run uses that environment's lab address unless an address is given explicitly, in which case the explicit address wins. The known environments are "local" (the default) and "ci"; an environment supplies only a default lab address and its name in the run output, and nothing else differs between them.
    - [ ] When the engineer sets an environment name that is not known, then the run stops before any check with a message naming the value given and listing the known names.
    - [ ] When any run starts, then its output shows, once, the lab address, browser and environment in use, and shows the access key only as hidden (for example "***").
    - [ ] When a reader searches the project's checks and helpers for a lab address, access key or browser name, then none is written into a check; each comes only from the settings.

### 2.6. A Run Log That Is Safe to Share

- **As an** engineer investigating a failure I could not watch, **I want** a written record of what each check asked the lab and what the lab answered, **so that** I can find the cause from the record alone.
  - **Acceptance Criteria:**
    - [ ] When any workflow check talks to the lab, then the run log records, for each request, which check made it, what was asked for (the kind of request and the lab address path), the answer's status, how long it took, and the identifiers involved (patient, order and sample identifiers).
    - [ ] When a check fails, then the engineer can find every log line belonging to that check by searching for the check's name.
    - [ ] When any run finishes, then the access key, the sign-in password and any patient name do not appear anywhere in the run log, in the run output or in the saved reports, whether the checks passed or failed.
    - [ ] When the engineer runs with default settings, then the console shows a short summary per request, and the full detail goes to one log file per test family (workflow checks, screen checks), kept with that family's run results and removed by the usual clean-up command.
    - [ ] When the engineer runs with the documented "more detail" option, then the log also includes the bodies of requests and answers, with secrets and patient names still hidden.
    - [ ] When a reader searches the framework for direct printing to the console, then they find none; everything goes through the run log.

---

## 3. Scope and Boundaries

### In-Scope

- Labels on every workflow and screen check ("api", "ui", "smoke", "regression") and one documented command per subset.
- Grouping checks that differ only by input into one check with a list of inputs, each input named in the output.
- Running workflow and screen checks side by side, with a documented "one at a time" option, proven by repeated runs.
- One shared set of test-data recipes with valid defaults and unique, obviously fake data per check.
- Lab address, access key, browser and environment set from outside the project, with clear early errors for unknown values.
- A run log of what checks asked the lab and what it answered, with secrets and patient names hidden.
- README updates for every new command and option.

### Out-of-Scope

- The automated build pipeline and published test reports in it (**Phase 3 — CI/CD Pipeline**). This specification only makes sure the settings and labels are ready for it, for example the "ci" environment name.
- The full README rewrite and the project walkthrough (**Phase 3 — Documentation & Review Readiness**); only the new commands and options are added here.
- The optional Cucumber demonstration (**Phase 3 — Optional BDD Layer**).
- Any change to the lab or its screens.
- Running checks on several machines at once, cloud browser farms, or running each check in several browsers within one run.
- A real second environment: "ci" may point at the same kind of locally started lab.
- Storing logs anywhere except alongside the run's results, log dashboards, and alerting.
- Adding new checks for lab behaviour that is not already checked; new checks appear here only where a requirement above needs proof.

---

## Change Log

_Dated amendments made after the spec was first written — typically by `/awos:spec` in Update Mode when a bug fix changed documented behavior. Each entry records the date, the source reference (bug id or fix description), and what behavior changed and why. Leave empty until the first amendment._

- [YYYY-MM-DD] — [source reference] — [what behavior changed and why]
