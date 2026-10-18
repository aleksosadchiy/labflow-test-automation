// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.tests.selfcheck.guards;

import com.labflow.tests.selfcheck.guards.support.FeatureFiles;
import com.labflow.tests.selfcheck.guards.support.FeatureFiles.Feature;
import com.labflow.tests.selfcheck.guards.support.FeatureFiles.Scenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the labels (functional-spec.md section 2.1). The workflow checks are Gherkin scenarios
 * (spec 008), so a label is a tag in a feature file: the family tag and {@code @regression} sit on
 * the {@code Feature:} line and reach every scenario of it, each scenario carries exactly one of
 * {@code @accepted} and {@code @refused}, and {@code @smoke} is on a short, fixed list of scenarios. So
 * a run such as {@code -Dgroups=smoke} cannot silently grow, shrink or lose a label. These guards
 * read the feature files as plain text and need no lab.
 */
class LabelGuardTest {

    /** The smoke scenarios of the workflow family, as {@code Feature name › scenario name}. Changing this list is a deliberate decision. */
    private static final Set<String> SMOKE_ALLOWLIST = Set.of(
            "01. Getting access › No key: looking a sample up is refused",
            "04. Registering a sample › A new sample starts at Accessioned",
            "09. The whole journey › A sample goes from registration to its report");

    private static final String FAMILY_TAG = "@api";
    private static final String REGRESSION_TAG = "@regression";
    private static final String SMOKE_TAG = "@smoke";
    private static final Set<String> SUB_GROUP_TAGS = Set.of("@accepted", "@refused");

    @Test
    @DisplayName("Every feature file carries the api and regression tags on its Feature line, and nothing else there, so every scenario inherits exactly them")
    void everyFeatureCarriesTheFamilyAndRegressionTags() {
        List<String> offences = new ArrayList<>();
        for (Feature feature : FeatureFiles.all()) {
            if (feature.tags().size() != 2 || !Set.copyOf(feature.tags()).equals(Set.of(FAMILY_TAG, REGRESSION_TAG))) {
                offences.add(feature.file() + ": Feature line has " + feature.tags());
            }
        }

        assertThat(offences)
                .as("each Feature: line must carry exactly %s and %s; found: %s", FAMILY_TAG, REGRESSION_TAG, String.join(" | ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("Every scenario carries exactly one of accepted and refused, and no tag of its own beyond smoke, so a typo cannot hide a check from a run")
    void scenariosUseTheAgreedTagsOnly() {
        List<String> offences = new ArrayList<>();
        for (Feature feature : FeatureFiles.all()) {
            assertThat(feature.scenarios()).as("scenarios found in %s", feature.file()).isNotEmpty();
            for (Scenario scenario : feature.scenarios()) {
                String where = feature.file() + ":" + scenario.line() + " '" + scenario.name() + "'";
                long subGroups = scenario.tags().stream().filter(SUB_GROUP_TAGS::contains).count();
                if (subGroups != 1) {
                    offences.add(where + " has " + subGroups + " of @accepted/@refused: " + scenario.tags());
                }
                scenario.tags().stream()
                        .filter(tag -> !SUB_GROUP_TAGS.contains(tag) && !tag.equals(SMOKE_TAG))
                        .forEach(tag -> offences.add(where + " has the tag " + tag + " that is not agreed"));
                if (!scenario.exampleTags().isEmpty()) {
                    offences.add(where + " has tags on its Examples: " + scenario.exampleTags());
                }
            }
            if (!feature.strayTags().isEmpty()) {
                offences.add(feature.file() + " has tags on nothing the labels are read from: " + feature.strayTags());
            }
        }

        assertThat(offences)
                .as("scenarios must use the agreed tags only; found: %s", String.join(" | ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("Exactly the agreed workflow scenarios are labelled smoke, so a change to the smoke set is deliberate")
    void smokeChecksAreExactlyTheAgreedOnes() {
        Set<String> smoke = new TreeSet<>(FeatureFiles.smokeScenarios());

        assertThat(smoke).as("scenarios tagged @smoke in the workflow feature files").isEqualTo(SMOKE_ALLOWLIST);
    }
}
