package com.labflow.tests.hooks;

import com.labflow.common.RunLog;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.util.ResultsUtils;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * What the Jupiter extensions did for the lab checks, rebuilt as Cucumber hooks.
 *
 * <p><b>The check name in the log.</b> {@link #putCheckNameIntoTheLog} sets the SLF4J MDC key
 * {@value RunLog#CHECK_NAME_KEY} to {@code <Feature name> › <scenario name>} (for an outline
 * row, the row's own name), so every run-log line of a scenario carries it. Cucumber runs hooks
 * and steps of one scenario on one thread, so the thread-local MDC reaches the steps;
 * {@link #clearTheCheckName} removes it afterwards.
 *
 * <p><b>The report's grouping.</b> {@link #labelTheReportEntry} sets, from the scenario's tags and
 * its feature, the labels the grouped view and the Suites view are built from: {@code epic} = the
 * family, {@code story} = the sub-group, and {@code parentSuite} / {@code suite} / {@code subSuite}
 * = family / topic / sub-group. Allure's Cucumber adapter has already set a {@code feature} label
 * (the topic) and a default {@code story}; the old values are replaced, so the result carries
 * exactly one of each.
 */
public class ScenarioHooks {

    private static final String API_TAG = "@api";
    private static final String ACCEPTED_TAG = "@accepted";
    private static final String REFUSED_TAG = "@refused";

    private static final String WORKFLOW_FAMILY = "API tests";

    /** Labels this hook owns: whatever the adapter set under these names is dropped first. */
    private static final Set<String> OWNED = Set.of("epic", "story", "parentSuite", "suite", "subSuite");

    private static final ConcurrentMap<String, String> FEATURE_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> DESCRIPTION_LINES = new ConcurrentHashMap<>();

    /** Runs first, so even the Given steps log under the scenario's name. */
    @Before(order = 0)
    public void putCheckNameIntoTheLog(Scenario scenario) {
        nameThisHook("Naming the check in the run log");
        MDC.put(RunLog.CHECK_NAME_KEY, featureName(scenario) + " › " + scenario.getName());
    }

    @Before(order = 1)
    public void labelTheReportEntry(Scenario scenario) {
        nameThisHook("Placing the check in the report's groups");
        String topic = featureName(scenario);
        String family = familyOf(scenario.getSourceTagNames());
        String subGroup = subGroupOf(scenario.getSourceTagNames());
        int descriptionLines = featureDescriptionLines(scenario);
        // A Before hook is recorded by the adapter as a fixture, so Allure's "current test case" is
        // not the scenario here; the scenario's result is addressed by its id instead.
        Allure.getLifecycle().updateTestCase(scenario.getId(), result -> {
            result.getLabels().removeIf(label -> OWNED.contains(label.getName()));
            // The adapter lists an outline row's example values as parameters, and the report shows
            // them in grey beside the name. The row's own name already says which row it is.
            result.setParameters(new ArrayList<>());
            // The adapter keeps the feature file's line breaks, so the report would break sentences
            // mid-line. The lines become paragraphs; a blank line stays a paragraph break.
            String paragraphs = asParagraphs(result.getDescription(), descriptionLines);
            result.setDescription(paragraphs);
            // The report shows plain text with a line break but no gap; as HTML each paragraph is a block.
            result.setDescriptionHtml(paragraphs == null ? null : asHtml(paragraphs));
            // Without the example values, the report tool tells two entries apart only by the adapter's
            // test case id (file and name) and history id (file and line). The api and the ui scenario
            // of one topic can share a name, a file name and a line, and the tool would then fold one
            // of them into the other as a "retry" and list only one. The family makes both unique.
            String where = scenario.getUri().getSchemeSpecificPart();
            result.setTestCaseId(ResultsUtils.md5(family + "|" + where + ":" + scenario.getName()));
            result.setHistoryId(ResultsUtils.md5(family + "|" + where + ":" + scenario.getLine()));
            result.getLabels().addAll(List.of(
                    ResultsUtils.createEpicLabel(family),
                    ResultsUtils.createStoryLabel(subGroup),
                    ResultsUtils.createParentSuiteLabel(family),
                    ResultsUtils.createSuiteLabel(topic),
                    ResultsUtils.createSubSuiteLabel(subGroup)));
        });
    }

    @After(order = 0)
    public void clearTheCheckName() {
        nameThisHook("Clearing the check name from the run log");
        MDC.remove(RunLog.CHECK_NAME_KEY);
    }

    /**
     * The adapter names the report's set-up and tear-down entry after the hook's code location
     * ({@code HookTestStep.getCodeLocation()}). The entry it opened is the one running on this thread,
     * so a hook gives it a plain name here.
     */
    static void nameThisHook(String plainName) {
        Allure.getLifecycle().updateFixture(fixture -> fixture.setName(plainName));
    }

    static String familyOf(Collection<String> tags) {
        if (tags.contains(API_TAG)) {
            return WORKFLOW_FAMILY;
        }
        throw new IllegalStateException("A scenario must carry the family tag @api, it has " + tags);
    }

    static String subGroupOf(Collection<String> tags) {
        boolean accepted = tags.contains(ACCEPTED_TAG);
        boolean refused = tags.contains(REFUSED_TAG);
        if (accepted == refused) {
            throw new IllegalStateException("A scenario must carry exactly one of @accepted and @refused, it has " + tags);
        }
        return accepted ? "Accepted" : "Refused";
    }

    /** The name after {@code Feature:} in the scenario's file; the topic, with its number. */
    private static String featureName(Scenario scenario) {
        String resource = scenario.getUri().getSchemeSpecificPart();
        return FEATURE_NAMES.computeIfAbsent(resource, ScenarioHooks::readFeatureName);
    }

    private static String readFeatureName(String resource) {
        return readFeatureFile(resource).lines()
                .map(String::strip)
                .filter(line -> line.startsWith("Feature:"))
                .map(line -> line.substring("Feature:".length()).strip())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Feature: line in " + resource));
    }

    private static String readFeatureFile(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Feature file not found on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    /**
     * How many lines the feature's own description has (the text under {@code Feature:} up to the
     * first tag or keyword, without blank lines at either end). The adapter writes the feature's
     * description and the scenario's one after the other with a single line break between them, so
     * this count is what tells where one ends.
     */
    private static int featureDescriptionLines(Scenario scenario) {
        return DESCRIPTION_LINES.computeIfAbsent(scenario.getUri().getSchemeSpecificPart(), resource -> {
            List<String> lines = readFeatureFile(resource).lines().map(String::strip).toList();
            int from = lines.indexOf(lines.stream().filter(l -> l.startsWith("Feature:")).findFirst().orElse("")) + 1;
            int to = from;
            while (to < lines.size() && !isKeywordOrTag(lines.get(to))) {
                to++;
            }
            while (to > from && lines.get(to - 1).isEmpty()) {
                to--;
            }
            while (from < to && lines.get(from).isEmpty()) {
                from++;
            }
            return to - from;
        });
    }

    private static boolean isKeywordOrTag(String line) {
        return line.startsWith("@") || line.startsWith("Scenario") || line.startsWith("Background")
                || line.startsWith("Rule") || line.startsWith("Example");
    }

    /**
     * The adapter's description (feature part, then scenario part, one line break between them) as
     * paragraphs: single line breaks inside a part become spaces, a blank line stays a paragraph
     * break, and the two parts are separated by one.
     */
    static String asParagraphs(String adapterText, int featureLines) {
        if (adapterText == null || adapterText.isBlank()) {
            return adapterText;
        }
        List<String> lines = adapterText.lines().toList();
        int split = Math.min(featureLines, lines.size());
        return java.util.stream.Stream.of(lines.subList(0, split), lines.subList(split, lines.size()))
                .map(ScenarioHooks::paragraphsOf)
                .filter(part -> !part.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    static String asHtml(String paragraphs) {
        return java.util.Arrays.stream(paragraphs.split("\\n\\n"))
                .map(p -> "<p>" + p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>")
                .collect(java.util.stream.Collectors.joining());
    }

    private static String paragraphsOf(List<String> lines) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                if (current.length() > 0) {
                    paragraphs.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(current.length() > 0 ? " " : "").append(line.strip());
            }
        }
        if (current.length() > 0) {
            paragraphs.add(current.toString());
        }
        return String.join("\n\n", paragraphs);
    }
}
