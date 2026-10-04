// @layer: integration
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.ui.tests.guard;

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
 * Acceptance check for functional-spec.md section 2.1 on the screen family: the documented
 * label expressions (README "Picking what to run") select exactly the checks the spec promises.
 * It asks the JUnit Platform to <em>discover</em> (never run) this module's own test classes with
 * the same tag filter Surefire builds from {@code -Dgroups=...}, so it needs no lab. The
 * workflow family has the mirror of this test in {@code api-tests}; together they give the six smoke checks.
 */
class LabelSelectionAcceptanceTest {

    /** The smoke checks of this family, as {@code Class#method}. */
    private static final Set<String> SMOKE = Set.of(
            "SignInTest#validSignInOpensSampleList",
            "SampleRegistrationTest#registeredSampleIsListedAsAccessioned",
            "ReportJourneyTest#fullJourneyThroughScreensEndsInReport");

    /** Every discovered test method of the module that passes the expression (null = no filter), keyed {@code Class#method} to its tags. */
    private static Map<String, Set<String>> discover(String tagExpression) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.labflow.ui.tests"));
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
    @DisplayName("Every screen check is labelled ui and regression, never api, and smoke only on the agreed three")
    void everyCheckResolvesToUiAndRegression() {
        Map<String, Set<String>> all = discover(null);
        Set<String> checks = checks(all);
        assertThat(checks).as("screen checks discovered").isNotEmpty();

        Set<String> smoke = new TreeSet<>();
        for (String check : checks) {
            Set<String> tags = all.get(check);
            assertThat(tags).as("labels of %s", check)
                    .contains(Labels.UI, Labels.REGRESSION)
                    .doesNotContain(Labels.API);
            if (tags.contains(Labels.SMOKE)) {
                smoke.add(shortName(check));
            }
        }
        assertThat(smoke).as("screen checks labelled smoke").isEqualTo(SMOKE);
    }

    @Test
    @DisplayName("The documented smoke command selects the three screen smoke checks plus only unlabelled self-checks")
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
    @DisplayName("The regression and ui commands select every screen check, and the api command selects none of them")
    void regressionAndFamilyCommandsSelectEveryScreenCheck() {
        Set<String> allChecks = checks(discover(null));

        assertThat(checks(discover("regression"))).as("regression command").isEqualTo(allChecks);
        assertThat(checks(discover("ui"))).as("ui command").isEqualTo(allChecks);
        assertThat(discover("api")).as("api command finds no screen check").isEmpty();
    }

    @Test
    @DisplayName("A label no check carries selects nothing at all, so the run can report that 0 checks ran")
    void unknownLabelSelectsNothing() {
        assertThat(discover("nosuch")).as("checks carrying the label 'nosuch'").isEmpty();
    }
}
