package com.labflow.ui.tests.hooks;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.labflow.common.RunLog;
import com.labflow.ui.config.BrowserChoice;
import com.labflow.ui.recording.TraceSettings;
import com.labflow.ui.tests.framework.evidence.ArtifactNames;
import com.labflow.ui.tests.framework.browser.BrowserPool;
import com.labflow.ui.tests.framework.evidence.ScenarioEvidence;
import com.labflow.ui.tests.framework.watchdog.ScenarioWatchdog;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.util.ResultsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The Cucumber hooks of the screen scenarios: the browser lifecycle, the scenario's name in the
 * log, the time limit and the report's grouping labels. (The logging and labelling hooks are a copy of api-tests'
 * {@code ScenarioHooks}; a shared place for them is a later slice's call.)
 *
 * <p><b>The browser.</b> {@link #openAFreshPage} gives every scenario its own context (1280x800)
 * and page on its worker thread's browser ({@link BrowserPool}) and starts tracing with
 * {@link TraceSettings#startOptions()}, the options {@code RecordingPause} resumes with after the
 * sign-in, so the password never lands in a trace. {@link #closeThePage} runs after the scenario:
 * on failure it saves a screenshot and the trace into
 * {@code target/ui-failures/<browser>/<topic>/<scenario>-<line>} and hands the folder to the
 * report; on success the trace is discarded. The context is closed in every case.
 * {@link #closeTheBrowsers} closes every browser once, after the last scenario.
 *
 * <p><b>Labels.</b> {@code epic} = "UI tests" (from {@code @ui}), {@code story} = the
 * sub-group, {@code parentSuite} / {@code suite} / {@code subSuite} = family / topic / sub-group.
 * Allure's adapter has set {@code feature} (the topic) and a default {@code story}; the old
 * values are replaced.
 */
public class UiHooks {

    private static final Logger LOG = LoggerFactory.getLogger(UiHooks.class);

    private static final String UI_TAG = "@ui";
    private static final String ACCEPTED_TAG = "@accepted";
    private static final String REFUSED_TAG = "@refused";

    private static final String SCREEN_FAMILY = "UI tests";

    /** Where failed scenarios' evidence goes: the same place as the Jupiter checks', per browser. */
    private static final Path ARTIFACTS_ROOT = Path.of("target", "ui-failures");

    /** Labels this hook owns: whatever the adapter set under these names is dropped first. */
    private static final Set<String> OWNED = Set.of("epic", "story", "parentSuite", "suite", "subSuite");

    private static final ConcurrentMap<String, String> FEATURE_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> DESCRIPTION_LINES = new ConcurrentHashMap<>();

    private final UiWorld world;
    private ScenarioWatchdog.Watch watch;

    public UiHooks(UiWorld world) {
        this.world = world;
    }

    /** Runs first, so even the Given steps log under the scenario's name. */
    @Before(order = 0)
    public void putCheckNameIntoTheLog(Scenario scenario) {
        nameThisHook("Naming the check in the run log");
        MDC.put(RunLog.CHECK_NAME_KEY, featureName(scenario) + " › " + scenario.getName());
    }

    /**
     * Starts the 2-minute limit right after the log name is set, so everything after it, the
     * browser opening included, is covered. The scenario ends the watch in {@link #stopTheWatchdog}.
     */
    @Before(order = 1)
    public void startTheWatchdog(Scenario scenario) {
        nameThisHook("Starting the time limit");
        watch = ScenarioWatchdog.forScenarios().start(featureName(scenario) + " › " + scenario.getName());
    }

    @Before(order = 2)
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

    @Before(order = 3)
    public void openAFreshPage() {
        nameThisHook("Opening a fresh browser page");
        Browser browser = BrowserPool.browserForThisThread();
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
        Page page;
        try {
            context.tracing().start(TraceSettings.startOptions());
            page = context.newPage();
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
        world.browserIs(context, page);
    }

    /** Runs before the log name is cleared, and always closes the context. */
    @After(order = 2)
    public void closeThePage(Scenario scenario) {
        nameThisHook("Closing the page, keeping a picture and recording on failure");
        BrowserContext context = world.browserContext();
        if (context == null) {
            return;
        }
        RuntimeException problem = capture(scenario, context);
        try {
            context.close();
        } catch (RuntimeException closeFailure) {
            if (problem == null) {
                problem = closeFailure;
            } else {
                problem.addSuppressed(closeFailure);
            }
        }
        if (problem == null) {
            return;
        }
        if (scenario.isFailed()) {
            // The scenario already failed; its own failure stays the one reported.
            LOG.warn("Could not finish the page of a failed scenario: {}", problem.toString());
            return;
        }
        throw problem;
    }

    /** After the page is closed, so a hang in closing it is covered too; before the name is cleared. */
    @After(order = 1)
    public void stopTheWatchdog() {
        nameThisHook("Stopping the time limit");
        if (watch != null) {
            watch.cancel();
        }
    }

    @After(order = 0)
    public void clearTheCheckName() {
        nameThisHook("Clearing the check name from the run log");
        MDC.remove(RunLog.CHECK_NAME_KEY);
    }

    /** Once, after the last scenario of the run. */
    @AfterAll
    public static void closeTheBrowsers() {
        BrowserPool.closeAll();
    }

    private RuntimeException capture(Scenario scenario, BrowserContext context) {
        try {
            if (scenario.isFailed()) {
                saveFailureArtifacts(scenario, context);
            } else {
                context.tracing().stop();
            }
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** Saves the screenshot and the trace; each save is tried even when the other failed. */
    private void saveFailureArtifacts(Scenario scenario, BrowserContext context) {
        Path folder = failureFolder(scenario);
        List<Throwable> problems = new ArrayList<>();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            problems.add(new UncheckedIOException("Could not create " + folder, e));
        }
        try {
            world.page().screenshot(new Page.ScreenshotOptions().setPath(folder.resolve("screenshot.png")));
        } catch (RuntimeException e) {
            problems.add(e);
        }
        try {
            context.tracing().stop(new Tracing.StopOptions().setPath(folder.resolve("trace.zip")));
        } catch (RuntimeException e) {
            problems.add(e);
        }
        // Whatever was saved also goes into the scenario's Allure result.
        ScenarioEvidence.attach(folder);
        if (!problems.isEmpty()) {
            RuntimeException failure = new RuntimeException("Could not save failure artifacts to " + folder);
            problems.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    /**
     * {@code target/ui-failures/<browser>/<topic>/<scenario>-<line>}, built only from the browser's
     * name, the sanitised topic and scenario names and the scenario's line number (which keeps
     * the rows of an outline apart): never from a value typed on a screen.
     */
    private static Path failureFolder(Scenario scenario) {
        String topic = ArtifactNames.sanitise(featureName(scenario));
        String leaf = ArtifactNames.sanitise(scenario.getName()) + "-" + scenario.getLine();
        return ARTIFACTS_ROOT.resolve(BrowserChoice.fromEnvironment().displayName()).resolve(topic).resolve(leaf);
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
        if (tags.contains(UI_TAG)) {
            return SCREEN_FAMILY;
        }
        throw new IllegalStateException("A scenario must carry the family tag @ui, it has " + tags);
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
        return FEATURE_NAMES.computeIfAbsent(resource, UiHooks::readFeatureName);
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
                .map(UiHooks::paragraphsOf)
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
