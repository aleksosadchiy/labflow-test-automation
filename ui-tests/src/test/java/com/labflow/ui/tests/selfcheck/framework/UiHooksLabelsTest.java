// @layer: unit
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.framework;

import com.labflow.common.RunLog;
import com.labflow.ui.tests.hooks.UiHooks;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.model.FixtureResult;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import io.qameta.allure.util.ResultsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What every screen scenario gets from its hooks (functional-spec.md of 008, sections 2.1, 2.2 and
 * 2.4): its topic and short name on every run-log line, and the report labels that build the grouped
 * view and the Suites view. The twin of {@code ScenarioHooksTest} in api-tests; the browser hooks
 * are covered by {@code UiHooksEvidenceTest}. No lab, no browser, no nested run.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("browser-pool")
class UiHooksLabelsTest {

    private static final String FEATURE = "features/04-registering-a-sample.feature";
    private static final String TOPIC = "04. Registering a sample";

    private final UiHooks hooks = new UiHooks(new UiWorld());
    private String resultId;

    @AfterEach
    void clearTheName() {
        MDC.remove(RunLog.CHECK_NAME_KEY);
    }

    private Scenario scenario(String name, List<String> tags) {
        Scenario scenario = ScenarioStandIn.of(FEATURE, name, 12, false, tags);
        resultId = scenario.getId();
        Allure.getLifecycle().scheduleTestCase(new TestResult().setUuid(resultId).setName(name)
                .setLabels(new ArrayList<>(List.of(
                        ResultsUtils.createFeatureLabel(TOPIC),
                        new Label().setName("story").setValue(TOPIC),
                        new Label().setName("epic").setValue("a default")))));
        return scenario;
    }

    private List<Label> labels() {
        AtomicReference<List<Label>> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(new ArrayList<>(result.getLabels())));
        return seen.get();
    }

    private static List<String> values(List<Label> labels, String name) {
        return labels.stream().filter(l -> l.getName().equals(name)).map(Label::getValue).toList();
    }

    @Test
    @DisplayName("Every run-log line of a screen scenario carries its topic and short name, and the name is gone when the scenario ends")
    void theCheckNameIsOnTheLogLinesWhileTheScenarioRuns() {
        Scenario scenario = scenario("A registered sample shows on screen " + UUID.randomUUID(), List.of("@ui", "@accepted"));

        hooks.putCheckNameIntoTheLog(scenario);
        assertThat(MDC.get(RunLog.CHECK_NAME_KEY)).isEqualTo(TOPIC + " › " + scenario.getName());

        hooks.clearTheCheckName();
        assertThat(MDC.get(RunLog.CHECK_NAME_KEY)).isNull();
    }

    @Test
    @DisplayName("A screen scenario is labelled with its family, topic and sub-group once each, and the Suites view repeats the same three names")
    void theReportLabelsBuildTheTree() {
        Scenario scenario = scenario("A registered sample shows on screen " + UUID.randomUUID(), List.of("@ui", "@refused"));

        hooks.labelTheReportEntry(scenario);

        List<Label> labels = labels();
        assertThat(values(labels, "epic")).containsExactly("UI tests");
        assertThat(values(labels, "story")).containsExactly("Refused");
        assertThat(values(labels, "feature")).containsExactly(TOPIC);
        assertThat(values(labels, "parentSuite")).containsExactly("UI tests");
        assertThat(values(labels, "suite")).containsExactly(TOPIC);
        assertThat(values(labels, "subSuite")).containsExactly("Refused");
    }

    @Test
    @DisplayName("A screen scenario with no family tag or no single sub-group tag is refused loudly instead of landing in the wrong group")
    void aScenarioThatCannotBePlacedFailsTheRun() {
        for (List<String> tags : List.of(List.of("@accepted"), List.of("@ui"), List.of("@ui", "@accepted", "@refused"), List.of("@api", "@accepted"))) {
            Scenario scenario = scenario("Unplaceable " + UUID.randomUUID(), tags);

            assertThatThrownBy(() -> hooks.labelTheReportEntry(scenario)).as("tags " + tags).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must carry");
        }
    }

    @Test
    @DisplayName("A row of an outline carries no example values as parameters, so the report shows nothing in grey beside its name")
    void exampleValuesAreNotShownBesideTheName() {
        Scenario scenario = scenario("A blank password is refused " + UUID.randomUUID(), List.of("@ui", "@refused"));
        Allure.getLifecycle().updateTestCase(resultId, result -> result.setParameters(new ArrayList<>(List.of(
                new Parameter().setName("case").setValue("A blank password"), new Parameter().setName("password").setValue("")))));

        hooks.labelTheReportEntry(scenario);

        AtomicReference<List<Parameter>> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(new ArrayList<>(result.getParameters())));
        assertThat(seen.get()).as("parameters left on the result").isEmpty();
    }

    @Test
    @DisplayName("Each hook gives the set-up or tear-down entry the adapter opened for it a plain name instead of the hook's method name")
    void everyHookNamesItsReportEntryInPlainLanguage() {
        Scenario scenario = scenario("A fixture is named " + UUID.randomUUID(), List.of("@ui", "@accepted"));
        // In the order a scenario runs them; the page hooks need no browser here (no page was opened).
        Map<String, Runnable> hookByPlainName = new LinkedHashMap<>();
        hookByPlainName.put("Naming the check in the run log", () -> hooks.putCheckNameIntoTheLog(scenario));
        hookByPlainName.put("Starting the time limit", () -> hooks.startTheWatchdog(scenario));
        hookByPlainName.put("Placing the check in the report's groups", () -> hooks.labelTheReportEntry(scenario));
        hookByPlainName.put("Closing the page, keeping a picture and recording on failure", () -> hooks.closeThePage(scenario));
        hookByPlainName.put("Stopping the time limit", () -> hooks.stopTheWatchdog());
        hookByPlainName.put("Clearing the check name from the run log", () -> hooks.clearTheCheckName());

        for (Map.Entry<String, Runnable> hook : hookByPlainName.entrySet()) {
            String container = "container-" + UUID.randomUUID();
            String fixture = "fixture-" + UUID.randomUUID();
            Allure.getLifecycle().startTestContainer(new TestResultContainer().setUuid(container));
            // What the adapter does for a hook: the entry is named after the hook's code location.
            Allure.getLifecycle().startPrepareFixture(container, fixture,
                    new FixtureResult().setName("com.labflow.ui.tests.hooks.UiHooks.aHook(io.cucumber.java.Scenario)"));
            try {
                hook.getValue().run();
                AtomicReference<String> name = new AtomicReference<>();
                Allure.getLifecycle().updateFixture(fixture, result -> name.set(result.getName()));
                assertThat(name.get()).isEqualTo(hook.getKey());
            } finally {
                Allure.getLifecycle().stopFixture(fixture);
                Allure.getLifecycle().stopTestContainer(container);
            }
        }
    }

    @Test
    @DisplayName("The report's test case id and history id are told apart by family, so the same file, name and line in both families are two entries, not a check and its retry")
    void theIdsTheReportToolFoldsOnIncludeTheFamily() {
        Scenario scenario = scenario("A blank sample type is refused " + UUID.randomUUID(), List.of("@ui", "@refused"));
        String adaptersHistoryId = ResultsUtils.md5("features/04-registering-a-sample.feature:12");
        String adaptersTestCaseId = ResultsUtils.md5("features/04-registering-a-sample.feature:" + scenario.getName());
        Allure.getLifecycle().updateTestCase(resultId, result -> result.setHistoryId(adaptersHistoryId).setTestCaseId(adaptersTestCaseId));

        hooks.labelTheReportEntry(scenario);

        AtomicReference<List<String>> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(List.of(result.getHistoryId(), result.getTestCaseId())));
        assertThat(seen.get()).containsExactly(
                ResultsUtils.md5("UI tests|features/04-registering-a-sample.feature:12"),
                ResultsUtils.md5("UI tests|features/04-registering-a-sample.feature:" + scenario.getName()));
        assertThat(seen.get()).doesNotContain(adaptersHistoryId, adaptersTestCaseId);
    }

    @Test
    @DisplayName("A description reaches the report as paragraphs: single line breaks become spaces, a blank line stays a paragraph break, the feature's and the scenario's text are two paragraphs")
    void descriptionLinesAreJoinedIntoParagraphs() {
        Scenario scenario = scenario("A described scenario " + UUID.randomUUID(), List.of("@ui", "@accepted"));
        // What the adapter stores: the feature's description, one line break, the scenario's (as in the feature file).
        String adapterText = String.join("\n", "  A sample is registered on the Create Sample screen for a patient the lab knows, and it then appears",
                "  in the Sample List as Accessioned. The screen refuses anything it cannot register and says why.",
                "    A scenario first paragraph that runs on", "    to a second line.", "", "    A second paragraph.");
        Allure.getLifecycle().updateTestCase(resultId, result -> result.setDescription(adapterText));

        hooks.labelTheReportEntry(scenario);

        AtomicReference<String> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(result.getDescription()));
        assertThat(seen.get()).isEqualTo("A sample is registered on the Create Sample screen for a patient the lab knows, and it then appears"
                + " in the Sample List as Accessioned. The screen refuses anything it cannot register and says why."
                + "\n\nA scenario first paragraph that runs on to a second line.\n\nA second paragraph.");
        AtomicReference<String> html = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> html.set(result.getDescriptionHtml()));
        assertThat(html.get()).as("one block per paragraph").startsWith("<p>").endsWith("<p>A second paragraph.</p>").contains("</p><p>");
        assertThat(seen.get()).as("no single line break left between two words").doesNotContainPattern("\\S\\n\\S");
    }
}
