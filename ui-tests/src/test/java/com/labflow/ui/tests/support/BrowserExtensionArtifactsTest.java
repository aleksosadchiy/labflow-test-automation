// @layer: integration
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.support;

import com.labflow.ui.config.BrowserChoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Proves FS 2.9 ¶2 and ¶4 - a failing screen check leaves a screenshot and a trace behind, named
 * after its scenario, and a passing one leaves nothing - as a real, repeatable regression test
 * instead of only the one-off manual break/fix the Slice 6 Verify task ran and then deleted the
 * evidence for. FS 2.9 ¶3 (opening the trace with the documented viewer) and the password-secrecy
 * criterion stay manual/covered elsewhere ({@code SignInTraceTest} already proves the password
 * never appears in a trace); a headless viewer smoke test would not add confidence beyond what
 * that test and the Slice 6/7 Verify already showed, so it is not duplicated here.
 *
 * <p>Runs two throwaway probe checks - one that always fails, one that always passes - directly
 * through the real {@code JupiterTestEngine} via {@link EngineTestKit}, so {@link BrowserExtension}
 * runs exactly as it would for a real check: a real Chromium context, real tracing, a real
 * {@code afterEach}. {@code EngineTestKit} talks to the engine directly rather than through
 * {@code LauncherFactory}, so {@code UiPreflightListener} never runs for it - this test needs no
 * running lab, only the same Chromium install every other screen check already needs.
 *
 * <p>Each probe extends {@link UiTest} (required by {@code BrowserExtension}, which rejects any
 * other test instance) but never calls {@link UiTest#signedIn()} or touches {@code lab}: it fails
 * or passes on a page that never navigated anywhere, which is enough to exercise the capture
 * path without needing the lab to be reachable.
 *
 * <p>Self-cleaning: whatever this test creates under {@code target/ui-failures/}, it deletes
 * before it finishes, pass or fail, so a green run of this test never leaves the directory the
 * completion evidence checks for its own absence.
 *
 * <p>{@link Execution @Execution(SAME_THREAD)}: both tests clean up under the same folders, so
 * when the whole run goes side by side one test's clean-up must not run during the other's
 * assertions. Both probe runs switch parallel execution off explicitly, so the outer run's
 * settings cannot change what they prove.
 */
@Execution(ExecutionMode.SAME_THREAD)
class BrowserExtensionArtifactsTest {

    private static final Path ARTIFACTS_ROOT = Path.of("target", "ui-failures");

    /** Failure artifacts sit one level deeper, under the browser's name (chromium by default). */
    private static final Path BROWSER_ROOT = ARTIFACTS_ROOT.resolve(BrowserChoice.fromEnvironment().displayName());

    @AfterEach
    void cleanUpAnyArtifactsThisTestCreated() throws IOException {
        deleteRecursively(BROWSER_ROOT.resolve(AlwaysFailingProbe.class.getSimpleName()));
        deleteRecursively(BROWSER_ROOT.resolve(AlwaysPassingProbe.class.getSimpleName()));
        deleteDirectoryIfEmpty(BROWSER_ROOT);
        deleteDirectoryIfEmpty(ARTIFACTS_ROOT);
    }

    @Test
    @DisplayName("A failing screen check leaves a screenshot and a trace behind, in a folder named after the browser and its scenario")
    void failingCheckLeavesScreenshotAndTrace() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AlwaysFailingProbe.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1).succeeded(0));

        Path folder = onlyChildOf(BROWSER_ROOT.resolve(AlwaysFailingProbe.class.getSimpleName()));
        assertThat(folder.resolve("screenshot.png"))
                .as("the failed probe's screenshot")
                .isRegularFile();
        assertThat(sizeOf(folder.resolve("screenshot.png")))
                .as("the failed probe's screenshot is not an empty file")
                .isGreaterThan(0L);
        assertThat(folder.resolve("trace.zip"))
                .as("the failed probe's trace")
                .isRegularFile();
        assertThat(sizeOf(folder.resolve("trace.zip")))
                .as("the failed probe's trace is not an empty file")
                .isGreaterThan(0L);
    }

    @Test
    @DisplayName("A passing screen check leaves nothing behind")
    void passingCheckLeavesNothing() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AlwaysPassingProbe.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        assertThat(BROWSER_ROOT.resolve(AlwaysPassingProbe.class.getSimpleName()))
                .as("no artifact folder is kept for a check that passed")
                .doesNotExist();
    }

    private static Path onlyChildOf(Path folder) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("Expected an artifact folder at " + folder.toAbsolutePath()
                    + " - BrowserExtension did not save anything for the failing probe");
        }
        try (Stream<Path> children = Files.list(folder)) {
            List<Path> found = children.toList();
            if (found.size() != 1) {
                fail("expected exactly one scenario folder under %s but found %s", folder, found);
            }
            return found.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + folder, e);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the size of " + file, e);
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

    private static void deleteDirectoryIfEmpty(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> children = Files.list(folder)) {
            if (children.findAny().isEmpty()) {
                Files.delete(folder);
            }
        }
    }

    /**
     * A throwaway check that always fails, run only through {@link EngineTestKit}, never by
     * Surefire's own discovery. Extends {@link UiTest} only - no {@code @ExtendWith} of its own,
     * exactly like every real screen check - because {@code @ExtendWith} is {@code @Inherited}:
     * redeclaring it here would register {@link BrowserExtension} a second time and open two
     * browser contexts for one test.
     */
    public static final class AlwaysFailingProbe extends UiTest {
        @Test
        @DisplayName("probe: deliberately fails so BrowserExtensionArtifactsTest can inspect what gets saved")
        void probeFails() {
            fail("deliberate failure, used only by BrowserExtensionArtifactsTest");
        }
    }

    /** A throwaway check that always passes, run only through {@link EngineTestKit}, never by Surefire's own discovery. */
    public static final class AlwaysPassingProbe extends UiTest {
        @Test
        @DisplayName("probe: deliberately passes so BrowserExtensionArtifactsTest can inspect what gets kept")
        void probePasses() {
            assertThat(true).isTrue();
        }
    }
}
