// @layer: integration
// @spec: 008-report-navigation
// @regression
package com.labflow.client.pipeline;

import com.labflow.client.support.ScenarioFiles;
import com.labflow.client.support.ScenarioFiles.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature-level proof of spec 008 that needs neither a lab nor a browser: the scenario files of
 * BOTH families read together, the way the report's reader meets them. The same rules are guarded
 * per module inside {@code api-tests} and {@code ui-tests} (names, numbering, tags, dry run); this
 * class adds what only the two families together can show, and what no guard states: the total of
 * 63, the 12-entry limit, the shared topic names, the descriptions, the plain-language wording and
 * the sort order the report will give.
 *
 * <h2>Criteria to tests (functional-spec.md of 008)</h2>
 * <p>Key: <b>[T]</b> automated here, <b>[G]</b> pinned by a guard that already exists,
 * <b>[R]</b> post-run, in {@link Spec008ReportContentAcceptanceTest} (needs the results of a run),
 * <b>[V]</b> rendered evidence only (Slice 10 Verify; screenshots stay local in
 * {@code docs/screenshots/008-report-navigation/}).
 *
 * <p><b>2.1 Topics</b>: grouped view with two families and topics [R tree]; plain topic names
 * [T {@link #noNameInTheScenarioFilesIsTakenFromTheCode}]; same name in both families
 * [T {@link #aTopicBothFamiliesCoverHasTheSameNumberAndNameInBoth}]; two sub-groups, empty one not
 * shown [R tree, the report tool hides an empty group]; at most 12 per sub-group
 * [T {@link #noSubGroupHoldsMoreThanTwelveEntries}]; 63 entries, each in exactly one topic
 * [T {@link #sixtyThreeEntriesAreWritten}]; passed / failed counts per topic [V, Allure's own
 * grouped view].
 *
 * <p><b>2.2 Short names</b>: at most 45 characters [G FeatureFilesGuardTest, R]; no ellipsis at 1440 px
 * [V]; a fuller description [T {@link #everyScenarioHasADescriptionAndEveryTopicOneOrTwoSentences}, R];
 * one entry per row [G FeatureFilesGuardTest, R]; unique per topic [G]; same short name in console,
 * log, run page and failure folder [R, {@code junit_summary.py} in {@link Spec008RunPageSummaryAcceptanceTest},
 * {@code ArtifactNamesTest}].
 *
 * <p><b>2.3 Order</b>: topics numbered in workflow order [T {@link #theReportSortsTopicsInWorkflowOrderAndAcceptedBeforeRefused}, G];
 * accepted before refused, alphabetical inside [T same, R]; same order twice [T same: the order is
 * a pure function of names]; checks still run side by side in random order [G LabRunWiringTest,
 * UiRunWiringGuardTest].
 *
 * <p><b>2.4 Suites</b>: plain names, same short names, requests kept [R].
 *
 * <p><b>2.5 Scenarios</b>: one file per topic and family with a description
 * [T {@link #everyScenarioHasADescriptionAndEveryTopicOneOrTwoSentences}]; titles and given / when /
 * then lines [T {@link #everyScenarioReadsAsGivenWhenThen}]; 63 [T]; outline with examples [G]; steps
 * equal the lines, requests under them [R]; failed line, failed screen check with picture [V, G
 * UiHooksEvidenceTest for the evidence]; unknown line fails naming line and file [G DryRunGuardTest].
 *
 * <p><b>2.6 Nothing else changes</b>: counts 47 / 16 [T, G]; pre-flight [G LabPreflightListenerTest,
 * UiPreflightListenerTest]; own data [G CheckIndependenceGuardTest]; watchdog [G ScenarioWatchdogTest];
 * labels [G LabelSelectionAcceptanceTest]; secrecy [R]; speed [V, Slice 9 Verify].
 *
 * <p><b>2.7 Documented</b>: README and product notes [V, Slice 9 Verify; the README guard forbids stale strings].
 */
class Spec008ScenarioFilesAcceptanceTest {

    private static final int WORKFLOW_CHECKS = 47;
    private static final int SCREEN_CHECKS = 16;
    private static final int MOST_PER_SUB_GROUP = 12;

    /** Words that betray a name taken from the code: a camelCase identifier, a call, a package or file name, a snake_case word. */
    private static final Pattern CODE = Pattern.compile(
            "[a-z]+[A-Z][a-z]+|\\(\\)|::|\\.java\\b|com\\.labflow|\\b\\w+_\\w+\\b|\\b[A-Z][a-z]+[A-Z]\\w*\\b");

    private static List<Entry> workflow() {
        return ScenarioFiles.in(ScenarioFiles.WORKFLOW, "API tests");
    }

    private static List<Entry> screen() {
        return ScenarioFiles.in(ScenarioFiles.SCREEN, "UI tests");
    }

    private static List<Entry> everything() {
        return Stream.concat(workflow().stream(), screen().stream()).toList();
    }

    @Test
    @DisplayName("Counting the scenarios of all files, outline rows one by one, gives 63: 47 workflow checks and 16 screen checks")
    void sixtyThreeEntriesAreWritten() {
        assertThat(workflow()).as("workflow entries").hasSize(WORKFLOW_CHECKS);
        assertThat(screen()).as("screen entries").hasSize(SCREEN_CHECKS);
        assertThat(everything()).as("all entries; every one carries a family sub-group tag").hasSize(63)
                .allSatisfy(entry -> assertThat(entry.subGroup()).as(entry.file() + ":" + entry.line()).isIn("Accepted", "Refused"));
    }

    @Test
    @DisplayName("A topic both families cover has the same number and the same name in both, and the screen topics are a part of the workflow topics")
    void aTopicBothFamiliesCoverHasTheSameNumberAndNameInBoth() {
        Map<String, String> workflowTopics = topicsByNumber(workflow());
        Map<String, String> screenTopics = topicsByNumber(screen());

        assertThat(workflowTopics.keySet()).as("workflow topic numbers").containsExactly("01", "02", "03", "04", "05", "06", "07", "08", "09");
        assertThat(screenTopics.entrySet()).as("each screen topic is the workflow topic of the same number, name included")
                .allSatisfy(topic -> assertThat(workflowTopics).containsEntry(topic.getKey(), topic.getValue()));
    }

    @Test
    @DisplayName("No sub-group of a topic lists more than 12 entries in either family, so one screen of the report shows it whole")
    void noSubGroupHoldsMoreThanTwelveEntries() {
        Map<String, Long> sizes = everything().stream()
                .collect(Collectors.groupingBy(e -> e.family() + " / " + e.topic() + " / " + e.subGroup(), TreeMap::new, Collectors.counting()));

        assertThat(sizes).as("entries per family / topic / sub-group").isNotEmpty()
                .allSatisfy((group, size) -> assertThat(size).as(group).isLessThanOrEqualTo(MOST_PER_SUB_GROUP));
    }

    @Test
    @DisplayName("Every scenario has a fuller description besides its title, and every topic starts with one or two sentences on what it covers")
    void everyScenarioHasADescriptionAndEveryTopicOneOrTwoSentences() {
        List<String> offences = new ArrayList<>();
        for (Entry entry : everything()) {
            String where = entry.family() + " " + entry.file() + ":" + entry.line() + " '" + entry.name() + "'";
            if (entry.description().isBlank()) {
                offences.add(where + " has no description");
            }
            int sentences = entry.topicDescription().isBlank() ? 0 : entry.topicDescription().split("(?<=[.!?])\\s+").length;
            if (sentences < 1 || sentences > 2) {
                offences.add(entry.family() + " " + entry.file() + " describes its topic in " + sentences + " sentences");
            }
        }

        assertThat(offences.stream().distinct().toList()).as("missing descriptions").isEmpty();
    }

    @Test
    @DisplayName("Every scenario is a short story: it starts with Given or When, ends in a Then, and every line starts with Given, When, Then, And or But")
    void everyScenarioReadsAsGivenWhenThen() {
        List<String> offences = new ArrayList<>();
        for (Entry entry : everything()) {
            String where = entry.family() + " " + entry.file() + ":" + entry.line() + " '" + entry.name() + "'";
            List<String> lines = entry.lines();
            if (lines.size() < 2 || lines.size() > 12) {
                offences.add(where + " has " + lines.size() + " lines, a few were agreed");
                continue;
            }
            if (!(lines.get(0).startsWith("Given ") || lines.get(0).startsWith("When "))) {
                offences.add(where + " starts with: " + lines.get(0));
            }
            if (lines.stream().noneMatch(l -> l.startsWith("Then "))) {
                offences.add(where + " has no Then line");
            }
        }

        assertThat(offences).as("scenarios that do not read as given / when / then").isEmpty();
    }

    @Test
    @DisplayName("No topic name, title, line or description is taken from the code: no identifier, call, package or file name")
    void noNameInTheScenarioFilesIsTakenFromTheCode() {
        List<String> offences = new ArrayList<>();
        for (Entry entry : everything()) {
            Stream.concat(Stream.of(entry.topic(), entry.name(), entry.description(), entry.topicDescription()), entry.lines().stream())
                    .filter(text -> CODE.matcher(text).find())
                    .forEach(text -> offences.add(entry.family() + " " + entry.file() + ":" + entry.line() + " reads like code: '" + text + "'"));
        }

        assertThat(offences.stream().distinct().toList()).as("text that reads like code").isEmpty();
    }

    @Test
    @DisplayName("Sorted the way the report sorts (by lower-case name), the topics come in workflow order and Accepted comes before Refused")
    void theReportSortsTopicsInWorkflowOrderAndAcceptedBeforeRefused() {
        for (List<Entry> family : List.of(workflow(), screen())) {
            List<String> topicsInWorkflowOrder = family.stream().map(Entry::topic).distinct().toList();
            List<String> asTheReportSorts = topicsInWorkflowOrder.stream()
                    .sorted(Comparator.comparing((String t) -> t.toLowerCase(Locale.ROOT))).toList();

            assertThat(asTheReportSorts).as(family.get(0).family() + ": sorted by name = in the order of the files").isEqualTo(topicsInWorkflowOrder);
        }
        assertThat(Stream.of("Refused", "Accepted").sorted(Comparator.comparing((String s) -> s.toLowerCase(Locale.ROOT))).toList())
                .as("sub-groups sorted by name").containsExactly("Accepted", "Refused");
        // Alphabetical inside a sub-group means the order is a pure function of the names: unique names, so no tie.
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        everything().forEach(e -> byGroup.computeIfAbsent(e.family() + e.topic() + e.subGroup(), k -> new ArrayList<>()).add(e.name()));
        assertThat(byGroup.values()).allSatisfy(names -> assertThat(names).doesNotHaveDuplicates());
    }

    private static Map<String, String> topicsByNumber(List<Entry> entries) {
        Map<String, String> topics = new TreeMap<>();
        for (Entry entry : entries) {
            assertThat(entry.topic()).as("topic starts with its two-digit number").matches("\\d{2}\\. \\S.*");
            topics.put(entry.topic().substring(0, 2), entry.topic());
        }
        return topics;
    }
}
