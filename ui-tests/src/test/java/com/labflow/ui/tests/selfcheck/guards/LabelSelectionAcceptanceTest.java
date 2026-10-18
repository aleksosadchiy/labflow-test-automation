// @layer: integration
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.ui.tests.selfcheck.guards;

import com.labflow.common.junit.Labels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance check for functional-spec.md section 2.1 on the screen family: the documented
 * label expressions (README "Picking what to run") select exactly the checks the spec promises.
 * It asks the JUnit Platform to <em>discover</em> (never run) the screen scenarios, through the
 * Cucumber engine and the {@code features} package the suite class selects, and this module's own
 * Jupiter self-checks, with the same tag filter Surefire builds from {@code -Dgroups=...}, so it
 * needs no lab or browser. The workflow family has the mirror of this test in {@code api-tests}; together
 * they give the six smoke checks.
 */
class LabelSelectionAcceptanceTest {

    private static final String SCENARIO_ENGINE = "cucumber";
    private static final String SELF_CHECK_ENGINE = "junit-jupiter";

    /** The smoke scenarios of this family, by name. */
    private static final Set<String> SMOKE = Set.of(
            "Valid details open the Sample List screen",
            "A registered sample is listed as Accessioned",
            "A sample is followed to its finished report");

    /** Every discovered scenario (one per example row) that passes the expression (null = no filter), keyed by its name to its tags. */
    private static Map<String, Set<String>> scenarios(String tagExpression) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("features"))
                .filters(EngineFilter.includeEngines(SCENARIO_ENGINE));
        if (tagExpression != null) {
            request.filters(TagFilter.includeTags(tagExpression));
        }
        TestPlan plan = LauncherFactory.create().discover(request.build());
        Map<String, Set<String>> found = new TreeMap<>();
        for (var root : plan.getRoots()) {
            for (TestIdentifier id : plan.getDescendants(root)) {
                if (id.isTest()) {
                    found.put(id.getDisplayName(), tagsOf(id));
                }
            }
        }
        return found;
    }

    /** Every discovered Jupiter test method of this module that passes the expression (null = no filter), keyed {@code Class#method} to its tags. */
    private static Map<String, Set<String>> selfChecks(String tagExpression) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.labflow.ui.tests"))
                .filters(EngineFilter.includeEngines(SELF_CHECK_ENGINE));
        if (tagExpression != null) {
            request.filters(TagFilter.includeTags(tagExpression));
        }
        TestPlan plan = LauncherFactory.create().discover(request.build());
        Map<String, Set<String>> found = new TreeMap<>();
        for (var root : plan.getRoots()) {
            for (TestIdentifier id : plan.getDescendants(root)) {
                if (id.getSource().orElse(null) instanceof MethodSource method) {
                    found.put(method.getClassName() + "#" + method.getMethodName(), tagsOf(id));
                }
            }
        }
        return found;
    }

    private static Set<String> tagsOf(TestIdentifier id) {
        Set<String> tags = new TreeSet<>();
        id.getTags().forEach(tag -> tags.add(tag.getName()));
        return tags;
    }

    @Test
    @DisplayName("Every screen scenario is labelled ui and regression, never api, and smoke only on the agreed three")
    void everyCheckResolvesToUiAndRegression() {
        Map<String, Set<String>> all = scenarios(null);
        assertThat(all).as("screen scenarios discovered through the cucumber engine").isNotEmpty();

        Set<String> smoke = new TreeSet<>();
        all.forEach((name, tags) -> {
            assertThat(tags).as("labels of scenario '%s'", name)
                    .contains(Labels.UI, Labels.REGRESSION)
                    .doesNotContain(Labels.API);
            if (tags.contains(Labels.SMOKE)) {
                smoke.add(name);
            }
        });
        assertThat(smoke).as("screen scenarios labelled smoke").isEqualTo(SMOKE);
    }

    @Test
    @DisplayName("The documented smoke command selects the three screen smoke scenarios plus only unlabelled self-checks")
    void smokeCommandSelectsSmokeChecksAndSelfChecks() {
        Map<String, Set<String>> selected = scenarios("smoke | !(api | ui)");
        assertThat(selected.keySet()).as("scenarios the smoke command runs").isEqualTo(SMOKE);

        Map<String, Set<String>> selectedSelfChecks = selfChecks("smoke | !(api | ui)");
        selectedSelfChecks.forEach((key, tags) ->
                assertThat(tags).as("labels of self-check %s", key).doesNotContain(Labels.API, Labels.UI));
        assertThat(selectedSelfChecks)
                .as("the smoke command also runs the framework's self-checks").isNotEmpty();
    }

    @Test
    @DisplayName("The regression and ui commands select every screen scenario, and the api command selects none of them")
    void regressionAndFamilyCommandsSelectEveryScenario() {
        Set<String> allScenarios = scenarios(null).keySet();

        assertThat(scenarios("regression").keySet()).as("regression command").isEqualTo(allScenarios);
        assertThat(scenarios("ui").keySet()).as("ui command").isEqualTo(allScenarios);
        assertThat(scenarios("api")).as("api command finds no screen scenario").isEmpty();
    }

    @Test
    @DisplayName("A label no check carries selects nothing at all, so the run can report that 0 checks ran")
    void unknownLabelSelectsNothing() {
        assertThat(scenarios("nosuch")).as("scenarios carrying the label 'nosuch'").isEmpty();
        assertThat(selfChecks("nosuch")).as("self-checks carrying the label 'nosuch'").isEmpty();
    }
}
