// @layer: unit
// @spec: 008-report-navigation
// @regression
package com.labflow.tests.selfcheck.framework;

import com.labflow.common.RunLog;
import com.labflow.tests.hooks.ScenarioHooks;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.model.FixtureResult;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import io.qameta.allure.util.ResultsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * What every workflow scenario gets from its hooks (functional-spec.md of 008, sections 2.1, 2.2 and
 * 2.4): the scenario's topic and name on every run-log line, and the report labels that build the
 * grouped view and the Suites view. The hooks' own methods are called with a stand-in for
 * Cucumber's {@code Scenario}; no lab and no nested run are needed. Allure's global lifecycle holds
 * one running result for the length of a test, never written to disk.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("allure-lifecycle")
class ScenarioHooksTest {

    private static final String FEATURE = "features/04-registering-a-sample.feature";
    private static final String TOPIC = "04. Registering a sample";

    private final ScenarioHooks hooks = new ScenarioHooks();
    private String resultId;

    @BeforeEach
    void startAResult() {
        resultId = "stand-in-" + UUID.randomUUID();
    }

    @AfterEach
    void clearTheName() {
        MDC.remove(RunLog.CHECK_NAME_KEY);
    }

    private Scenario scenario(String name, List<String> tags) {
        Scenario scenario = ScenarioStandIn.of(FEATURE, name, 12, false, tags);
        // The stand-in's id is "stand-in-<name>"; give the result that id.
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
    @DisplayName("Every run-log line of a scenario carries its topic and short name, and the name is gone when the scenario ends")
    void theCheckNameIsOnTheLogLinesWhileTheScenarioRuns() {
        Scenario scenario = scenario("A newly registered sample starts at Accessioned " + UUID.randomUUID(), List.of("@api", "@accepted"));

        hooks.putCheckNameIntoTheLog(scenario);
        assertThat(MDC.get(RunLog.CHECK_NAME_KEY)).isEqualTo(TOPIC + " › " + scenario.getName());

        hooks.clearTheCheckName();
        assertThat(MDC.get(RunLog.CHECK_NAME_KEY)).as("nothing left for the next scenario on this thread").isNull();
    }

    @Test
    @DisplayName("A scenario is labelled with its family, topic and sub-group once each, and the Suites view repeats the same three names")
    void theReportLabelsBuildTheTree() {
        Scenario scenario = scenario("A newly registered sample starts at Accessioned " + UUID.randomUUID(), List.of("@api", "@accepted", "@smoke"));

        hooks.labelTheReportEntry(scenario);

        List<Label> labels = labels();
        assertThat(values(labels, "epic")).containsExactly("API tests");
        assertThat(values(labels, "story")).containsExactly("Accepted");
        assertThat(values(labels, "feature")).containsExactly(TOPIC);
        assertThat(values(labels, "parentSuite")).containsExactly("API tests");
        assertThat(values(labels, "suite")).containsExactly(TOPIC);
        assertThat(values(labels, "subSuite")).containsExactly("Accepted");
    }

    @Test
    @DisplayName("A refused scenario lands in the Refused sub-group")
    void aRefusedScenarioIsInTheRefusedSubGroup() {
        Scenario scenario = scenario("A sample without a patient is refused " + UUID.randomUUID(), List.of("@api", "@refused"));

        hooks.labelTheReportEntry(scenario);

        assertThat(values(labels(), "story")).containsExactly("Refused");
        assertThat(values(labels(), "subSuite")).containsExactly("Refused");
    }

    @Test
    @DisplayName("A scenario with no family tag, no sub-group tag or both sub-group tags is refused loudly instead of landing in the wrong group")
    void aScenarioThatCannotBePlacedFailsTheRun() {
        for (List<String> tags : List.of(List.of("@accepted"), List.of("@api"), List.of("@api", "@accepted", "@refused"), List.of("@ui", "@accepted"))) {
            Scenario scenario = scenario("Unplaceable " + UUID.randomUUID(), tags);

            assertThatThrownBy(() -> hooks.labelTheReportEntry(scenario)).as("tags " + tags).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must carry");
        }
    }

    @Test
    @DisplayName("A row of an outline carries no example values as parameters, so the report shows nothing in grey beside its name")
    void exampleValuesAreNotShownBesideTheName() {
        Scenario scenario = scenario("A blank password is refused " + UUID.randomUUID(), List.of("@api", "@refused"));
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
        Scenario scenario = scenario("A fixture is named " + UUID.randomUUID(), List.of("@api", "@accepted"));
        Map<String, Runnable> hookByPlainName = new LinkedHashMap<>();
        hookByPlainName.put("Naming the check in the run log", () -> hooks.putCheckNameIntoTheLog(scenario));
        hookByPlainName.put("Placing the check in the report's groups", () -> hooks.labelTheReportEntry(scenario));
        hookByPlainName.put("Clearing the check name from the run log", () -> hooks.clearTheCheckName());

        for (Map.Entry<String, Runnable> hook : hookByPlainName.entrySet()) {
            String container = "container-" + UUID.randomUUID();
            String fixture = "fixture-" + UUID.randomUUID();
            Allure.getLifecycle().startTestContainer(new TestResultContainer().setUuid(container));
            // What the adapter does for a hook: the entry is named after the hook's code location.
            Allure.getLifecycle().startPrepareFixture(container, fixture,
                    new FixtureResult().setName("com.labflow.tests.hooks.ScenarioHooks.aHook(io.cucumber.java.Scenario)"));
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
        Scenario scenario = scenario("A blank sample type is refused " + UUID.randomUUID(), List.of("@api", "@refused"));
        String adaptersHistoryId = ResultsUtils.md5("features/04-registering-a-sample.feature:12");
        String adaptersTestCaseId = ResultsUtils.md5("features/04-registering-a-sample.feature:" + scenario.getName());
        Allure.getLifecycle().updateTestCase(resultId, result -> result.setHistoryId(adaptersHistoryId).setTestCaseId(adaptersTestCaseId));

        hooks.labelTheReportEntry(scenario);

        AtomicReference<List<String>> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(List.of(result.getHistoryId(), result.getTestCaseId())));
        assertThat(seen.get()).containsExactly(
                ResultsUtils.md5("API tests|features/04-registering-a-sample.feature:12"),
                ResultsUtils.md5("API tests|features/04-registering-a-sample.feature:" + scenario.getName()));
        assertThat(seen.get()).doesNotContain(adaptersHistoryId, adaptersTestCaseId);
    }

    @Test
    @DisplayName("A description reaches the report as paragraphs: single line breaks become spaces, a blank line stays a paragraph break, the feature's and the scenario's text are two paragraphs")
    void descriptionLinesAreJoinedIntoParagraphs() {
        Scenario scenario = scenario("A described scenario " + UUID.randomUUID(), List.of("@api", "@accepted"));
        // What the adapter stores: the feature's description, one line break, the scenario's (as in the feature file).
        String adapterText = String.join("\n", "  The lab registers a sample for a patient, optionally under an order, and every new sample starts at",
                "  Accessioned. A request that is missing something, names something unknown, or repeats a sample type",
                "  within one order is refused.",
                "    A scenario first paragraph that runs on", "    to a second line.", "", "    A second paragraph.");
        Allure.getLifecycle().updateTestCase(resultId, result -> result.setDescription(adapterText));

        hooks.labelTheReportEntry(scenario);

        AtomicReference<String> seen = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> seen.set(result.getDescription()));
        assertThat(seen.get()).isEqualTo("The lab registers a sample for a patient, optionally under an order, and every new sample starts at"
                + " Accessioned. A request that is missing something, names something unknown, or repeats a sample type"
                + " within one order is refused."
                + "\n\nA scenario first paragraph that runs on to a second line.\n\nA second paragraph.");
        AtomicReference<String> html = new AtomicReference<>();
        Allure.getLifecycle().updateTestCase(resultId, result -> html.set(result.getDescriptionHtml()));
        assertThat(html.get()).as("one block per paragraph").startsWith("<p>").endsWith("<p>A second paragraph.</p>").contains("</p><p>");
        assertThat(seen.get()).as("no single line break left between two words").doesNotContainPattern("\\S\\n\\S");
    }
}
