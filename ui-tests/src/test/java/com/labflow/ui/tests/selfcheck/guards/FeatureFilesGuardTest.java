// @layer: unit
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.guards;

import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.UiCredentials;
import com.labflow.ui.tests.selfcheck.guards.support.DryRun;
import com.labflow.ui.tests.selfcheck.guards.support.FeatureFiles;
import com.labflow.ui.tests.selfcheck.guards.support.FeatureFiles.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the shape of the screen feature files (spec 008, technical considerations 2.8): how many
 * scenario entries there are, how their names are written, how the topics are numbered, and that no
 * secret is written in a file. The scenario entries (an outline row is one entry, with the name the
 * report shows) come from a dry run of the engine ({@link DryRun}), so they are what a real run would
 * execute. The tags (one family tag per feature, one sub-group tag per scenario) are guarded by
 * {@code LabelGuardTest}. No lab is needed.
 */
class FeatureFilesGuardTest {

    /** The screen scenario entries of the project, outline rows expanded. Changing it is a deliberate decision. */
    private static final int SCREEN_ENTRIES = 16;

    private static final int LONGEST_NAME = 45;

    private static final Pattern TOPIC = Pattern.compile("^(\\d{2})\\. (\\S.*)$");
    private static final Pattern FILE = Pattern.compile("^(\\d{2})-.+\\.feature$");

    /** Where the workflow family keeps its feature files; the topics of the two families must agree. */
    private static final Path WORKFLOW_FEATURES = Path.of("..", "api-tests", "src", "test", "features");

    @Test
    @DisplayName("The screen feature files hold exactly the agreed number of scenario entries, outline rows counted one by one")
    void theNumberOfScenarioEntriesIsTheAgreedOne() {
        List<DryRun.Entry> entries = DryRun.ofTheFeatures().entries();

        assertThat(entries).as("scenario entries a run executes; if this is deliberate, change SCREEN_ENTRIES").hasSize(SCREEN_ENTRIES);
    }

    @Test
    @DisplayName("Every scenario name is at most 45 characters, outline rows included, so none is cut off at 1440 px")
    void scenarioNamesAreShortEnough() {
        List<String> offences = DryRun.ofTheFeatures().entries().stream()
                .filter(entry -> entry.name().length() > LONGEST_NAME)
                .map(entry -> entry.file() + ":" + entry.line() + " has " + entry.name().length() + " characters: '" + entry.name() + "'")
                .toList();

        assertThat(offences).as("names longer than %d characters", LONGEST_NAME).isEmpty();
    }

    @Test
    @DisplayName("No two scenario entries of one topic share a name, so every entry in the report can be told apart")
    void scenarioNamesAreUniqueWithinATopic() {
        Set<String> seen = new HashSet<>();
        List<String> offences = new ArrayList<>();
        for (DryRun.Entry entry : DryRun.ofTheFeatures().entries()) {
            if (!seen.add(entry.file() + "\u0000" + entry.name())) {
                offences.add(entry.file() + ":" + entry.line() + " repeats the name '" + entry.name() + "'");
            }
        }

        assertThat(offences).as("names used twice in one topic").isEmpty();
    }

    @Test
    @DisplayName("The topics are numbered in workflow order, each file name and topic name starting with the same two-digit number")
    void topicsAreNumberedInWorkflowOrder() {
        List<String> offences = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        for (Feature feature : FeatureFiles.all()) {
            Matcher topic = TOPIC.matcher(feature.name());
            Matcher file = FILE.matcher(feature.file());
            if (!topic.matches()) {
                offences.add(feature.file() + ": the topic '" + feature.name() + "' does not start with 'NN. '");
                continue;
            }
            if (!file.matches() || !file.group(1).equals(topic.group(1))) {
                offences.add(feature.file() + ": the file name does not start with the topic's number " + topic.group(1));
            }
            numbers.add(Integer.parseInt(topic.group(1)));
        }

        assertThat(offences).as("topics that are not numbered as agreed").isEmpty();
        assertThat(numbers).as("topic numbers in file order: strictly rising, so no number is used twice").isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Every screen topic also exists in the workflow family under the same number and the same name, so the report shows one topic list")
    void everyScreenTopicIsAlsoAWorkflowTopic() {
        Set<String> workflowTopics = FeatureFiles.in(WORKFLOW_FEATURES).stream().map(Feature::name).collect(Collectors.toSet());

        List<String> offences = FeatureFiles.all().stream()
                .filter(feature -> !workflowTopics.contains(feature.name()))
                .map(feature -> feature.file() + ": the topic '" + feature.name() + "' has no workflow topic of exactly that name")
                .toList();

        assertThat(offences).as("screen topics that differ from the workflow family's (number or name)").isEmpty();
    }

    @Test
    @DisplayName("No feature file holds the lab's documented access key or password, because a table value reaches the report as a parameter")
    void noFeatureFileHoldsASecret() throws IOException {
        List<String> secrets = secrets();
        List<String> offences = new ArrayList<>();
        for (Feature feature : FeatureFiles.all()) {
            List<String> lines = Files.readAllLines(Path.of("src", "test", "features", feature.file()));
            for (int i = 0; i < lines.size(); i++) {
                for (int s = 0; s < secrets.size(); s++) {
                    if (lines.get(i).contains(secrets.get(s))) {
                        offences.add(feature.file() + ":" + (i + 1) + " holds " + (s < 2 ? "an access key" : "a password") + " (value not shown)");
                    }
                }
            }
        }

        assertThat(offences.stream().distinct().toList())
                .as("secrets in feature files; a table holds a label and the step resolves it").isEmpty();
    }

    /** The access key as configured and the documented default key, then the password as configured and the documented default (the first two are keys); never printed. */
    private static List<String> secrets() {
        List<String> secrets = new ArrayList<>();
        secrets.add(LabConfig.fromEnvironment().apiKey().reveal());
        secrets.add(LabConfig.DEFAULT_API_KEY);
        secrets.add(UiCredentials.fromEnvironment().password().reveal());
        secrets.add(UiCredentials.DEFAULT_PASSWORD);
        return secrets;
    }
}
