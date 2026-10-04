// @layer: integration
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.tests;

import com.labflow.common.junit.Labels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
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
 * Acceptance check for functional-spec.md section 2.1 on the workflow family: the documented
 * label expressions (README "Picking what to run") select exactly the checks the spec promises.
 * It asks the JUnit Platform to <em>discover</em> (never run) this module's own test classes with
 * the same tag filter Surefire builds from {@code -Dgroups=...}, so it needs no lab. The screen
 * family has the mirror of this test in {@code ui-tests}; together they give the six smoke checks.
 */
class LabelSelectionAcceptanceTest {

    /** The smoke checks of this family, as {@code Class#method}. */
    private static final Set<String> SMOKE = Set.of(
            "FullJourneyTest#wholeJourneyFromPatientToReport",
            "PatientOrderSampleTest#newSampleIsAccessioned",
            "AuthenticationRejectionTest#readWithoutKeyIsRefused");

    /** Every discovered test method of the module that passes the expression (null = no filter), keyed {@code Class#method} to its tags. */
    private static Map<String, Set<String>> discover(String tagExpression) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.labflow.tests"));
        if (tagExpression != null) {
            request.filters(TagFilter.includeTags(tagExpression));
        }
        TestPlan plan = LauncherFactory.create().discover(request.build());
        Map<String, Set<String>> found = new TreeMap<>();
        for (var root : plan.getRoots()) {
            for (TestIdentifier id : plan.getDescendants(root)) {
                if (id.getSource().orElse(null) instanceof MethodSource method) {
                    Set<String> tags = new TreeSet<>();
                    id.getTags().forEach(tag -> tags.add(tag.getName()));
                    found.put(method.getClassName() + "#" + method.getMethodName(), tags);
                }
            }
        }
        return found;
    }

    private static boolean isCheck(String key) {
        return key.contains(".happy.") || key.contains(".rejection.");
    }

    private static Set<String> checks(Map<String, Set<String>> discovered) {
        Set<String> checks = new TreeSet<>();
        discovered.keySet().stream().filter(LabelSelectionAcceptanceTest::isCheck).forEach(checks::add);
        return checks;
    }

    private static String shortName(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    @Test
    @DisplayName("Every workflow check is labelled api and regression, never ui, and smoke only on the agreed three")
    void everyCheckResolvesToApiAndRegression() {
        Map<String, Set<String>> all = discover(null);
        Set<String> checks = checks(all);
        assertThat(checks).as("workflow checks discovered").isNotEmpty();

        Set<String> smoke = new TreeSet<>();
        for (String check : checks) {
            Set<String> tags = all.get(check);
            assertThat(tags).as("labels of %s", check)
                    .contains(Labels.API, Labels.REGRESSION)
                    .doesNotContain(Labels.UI);
            if (tags.contains(Labels.SMOKE)) {
                smoke.add(shortName(check));
            }
        }
        assertThat(smoke).as("workflow checks labelled smoke").isEqualTo(SMOKE);
    }

    @Test
    @DisplayName("The documented smoke command selects the three workflow smoke checks plus only unlabelled self-checks")
    void smokeCommandSelectsSmokeChecksAndSelfChecks() {
        Map<String, Set<String>> selected = discover("smoke | !(api | ui)");

        Set<String> selectedChecks = new TreeSet<>();
        checks(selected).forEach(check -> selectedChecks.add(shortName(check)));
        assertThat(selectedChecks).as("lab checks the smoke command runs").isEqualTo(SMOKE);

        selected.forEach((key, tags) -> {
            if (!isCheck(key)) {
                assertThat(tags).as("labels of self-check %s", key).doesNotContain(Labels.API, Labels.UI);
            }
        });
        assertThat(selected.keySet().stream().filter(key -> !isCheck(key)))
                .as("the smoke command also runs the framework's self-checks").isNotEmpty();
    }

    @Test
    @DisplayName("The regression and api commands select every workflow check, and the ui command selects none of them")
    void regressionAndFamilyCommandsSelectEveryWorkflowCheck() {
        Set<String> allChecks = checks(discover(null));

        assertThat(checks(discover("regression"))).as("regression command").isEqualTo(allChecks);
        assertThat(checks(discover("api"))).as("api command").isEqualTo(allChecks);
        assertThat(discover("ui")).as("ui command finds no workflow check").isEmpty();
    }

    @Test
    @DisplayName("A label no check carries selects nothing at all, so the run can report that 0 checks ran")
    void unknownLabelSelectsNothing() {
        assertThat(discover("nosuch")).as("checks carrying the label 'nosuch'").isEmpty();
    }
}
