// @layer: integration
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.framework;

import com.labflow.ui.tests.hooks.UiHooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.labflow.ui.config.BrowserChoice;
import com.labflow.ui.tests.framework.browser.BrowserPool;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.FileSystemResultsWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * A self-check of the framework (plain Jupiter, no family label, so it is not counted as a lab
 * check): what the failure evidence of a screen check must be, pinned against {@link UiHooks}.
 * A failed scenario leaves a screenshot and a trace in a folder named after it and attaches both to
 * the report; a passing one leaves nothing.
 *
 * <p>It calls the hooks' own methods ({@code openAFreshPage}, {@code closeThePage}) with a
 * stand-in for Cucumber's {@code Scenario} ({@link ScenarioStandIn}) and a real headless Chromium
 * on a blank page. No lab and no nested Cucumber run is needed: the hooks never touch the lab, and
 * a nested run would need a scenario that fails on purpose inside the suite.
 *
 * <p>Allure is pointed at a throw-away results folder for the length of each test, with one
 * running result on the test's own thread, so the attachments can be read back and no stray
 * file reaches the real {@code allure-results}. The scenario's name is unique per test, so the
 * test only ever deletes the folders it made itself.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("browser-pool")
class UiHooksEvidenceTest {

    private static final String FEATURE = "features/01-getting-access.feature";
    private static final String TOPIC_FOLDER = "01-getting-access";
    private static final Path BROWSER_ROOT =
            Path.of("target", "ui-failures").resolve(BrowserChoice.fromEnvironment().displayName());

    @TempDir
    Path allureResults;

    private AllureLifecycle realLifecycle;
    private AllureLifecycle lifecycle;
    private String resultId;
    private final List<Path> createdByThisTest = new ArrayList<>();
    private boolean browserRootExisted;
    private boolean topicExisted;

    @BeforeEach
    void pointAllureAtAScratchFolder() {
        browserRootExisted = Files.isDirectory(BROWSER_ROOT);
        topicExisted = Files.isDirectory(BROWSER_ROOT.resolve(TOPIC_FOLDER));
        realLifecycle = Allure.getLifecycle();
        lifecycle = new AllureLifecycle(new FileSystemResultsWriter(allureResults));
        Allure.setLifecycle(lifecycle);
        resultId = UUID.randomUUID().toString();
        lifecycle.scheduleTestCase(new TestResult().setUuid(resultId).setName("probe"));
    }

    @AfterEach
    void putEverythingBack() throws IOException {
        Allure.setLifecycle(realLifecycle);
        BrowserPool.closeAll();
        for (Path folder : createdByThisTest) {
            deleteRecursively(folder);
        }
        if (!topicExisted) {
            deleteIfEmpty(BROWSER_ROOT.resolve(TOPIC_FOLDER));
        }
        if (!browserRootExisted) {
            deleteIfEmpty(BROWSER_ROOT);
            deleteIfEmpty(BROWSER_ROOT.getParent());
        }
    }

    @Test
    @DisplayName("A failed scenario leaves a screenshot and a trace in a folder named after the browser, the topic and the scenario")
    void failedScenarioLeavesScreenshotAndTrace() {
        String unique = uniqueSuffix();
        Path expected = BROWSER_ROOT.resolve(TOPIC_FOLDER).resolve("probe-a-failing-scenario-" + unique + "-42");
        createdByThisTest.add(expected);

        runOnItsOwnThread(ScenarioStandIn.of(FEATURE, "Probe a failing scenario " + unique, 42, true));

        assertThat(expected).as("the failed scenario's folder").isDirectory();
        assertThat(expected.resolve("screenshot.png")).as("the screenshot").isRegularFile();
        assertThat(sizeOf(expected.resolve("screenshot.png"))).as("the screenshot is not empty").isGreaterThan(0L);
        assertThat(expected.resolve("trace.zip")).as("the trace").isRegularFile();
        assertThat(sizeOf(expected.resolve("trace.zip"))).as("the trace is not empty").isGreaterThan(0L);
    }

    @Test
    @DisplayName("A failed scenario attaches both its screenshot and its trace to the report")
    void failedScenarioAttachesBoth() {
        String unique = uniqueSuffix();
        createdByThisTest.add(BROWSER_ROOT.resolve(TOPIC_FOLDER).resolve("probe-an-attaching-scenario-" + unique + "-7"));

        runOnItsOwnThread(ScenarioStandIn.of(FEATURE, "Probe an attaching scenario " + unique, 7, true));

        List<Attachment> attached = attachmentsOfTheRunningResult();
        assertThat(attached).extracting(Attachment::getType)
                .as("a picture and a zip archive are attached")
                .containsExactlyInAnyOrder("image/png", "application/zip");
        assertThat(attached).extracting(Attachment::getName)
                .anyMatch(name -> name.contains("Screenshot"))
                .anyMatch(name -> name.contains("trace"));
    }

    @Test
    @DisplayName("A passing scenario leaves no folder and attaches nothing")
    void passingScenarioLeavesNothing() {
        String unique = uniqueSuffix();
        Path wouldBe = BROWSER_ROOT.resolve(TOPIC_FOLDER).resolve("probe-a-passing-scenario-" + unique + "-9");
        createdByThisTest.add(wouldBe);

        runOnItsOwnThread(ScenarioStandIn.of(FEATURE, "Probe a passing scenario " + unique, 9, false));

        assertThat(wouldBe).as("no evidence folder is kept for a scenario that passed").doesNotExist();
        assertThat(attachmentsOfTheRunningResult()).as("nothing is attached for a scenario that passed").isEmpty();
    }

    /** Opens and closes the page through the hooks on a thread of its own, like a Cucumber worker. */
    private void runOnItsOwnThread(Scenario scenario) {
        Throwable[] thrown = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                // Allure's "current result" belongs to a thread, so it is started on the worker's.
                lifecycle.startTestCase(resultId);
                UiHooks hooks = new UiHooks(new UiWorld());
                hooks.openAFreshPage();
                hooks.closeThePage(scenario);
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "probe-worker");
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        assertThat(thrown[0]).as("the hooks ran without a problem").isNull();
    }

    private List<Attachment> attachmentsOfTheRunningResult() {
        List<Attachment> found = new ArrayList<>();
        lifecycle.updateTestCase(resultId, result -> found.addAll(result.getAttachments()));
        return found;
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void deleteIfEmpty(Path folder) throws IOException {
        if (folder != null && Files.isDirectory(folder)) {
            try (Stream<Path> children = Files.list(folder)) {
                if (children.findAny().isEmpty()) {
                    Files.delete(folder);
                }
            }
        }
    }
}
