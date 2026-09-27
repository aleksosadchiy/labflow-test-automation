package com.labflow.ui.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.labflow.ui.recording.TraceSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Owns the browser lifecycle for every screen check.
 *
 * <p>One {@link Playwright} driver and one Chromium {@link Browser} are created lazily, the
 * first time any test needs them, and stored in the <b>root</b> {@link ExtensionContext.Store}
 * as a {@link ExtensionContext.Store.CloseableResource}. JUnit closes that resource itself once
 * the whole test run ends, so there is no {@code @BeforeAll}/{@code @AfterAll} to write and no
 * static field to leak state between test classes. Creating the driver once per run also avoids
 * re-extracting the Node driver into {@code java.io.tmpdir} for every single test.
 *
 * <p>Each test method then gets its own fresh {@link BrowserContext} (fixed 1280x800 viewport)
 * and {@link Page}, assigned directly to the {@link UiTest} instance's {@code page} field before
 * the test body runs, and closed again once it finishes. Tests never share cookies, local
 * storage or navigation history, and {@code Browser.newContext()} is safe to call concurrently,
 * so this is already shaped for parallel execution (a later spec) even though nothing here runs
 * in parallel yet.
 *
 * <p>{@code headless} defaults to {@code true} ({@code -Dheaded=true} shows the browser window);
 * {@code slowMo} defaults to {@code 0} ({@code -DslowMo=<ms>} slows every action down, for
 * debugging).
 *
 * <p><b>Failure artifacts (tech spec §2.5, FS 2.9):</b> tracing starts right after the context is
 * created, with the same {@link TraceSettings#startOptions()} {@link com.labflow.ui.recording.RecordingPause}
 * uses to resume after the real password is gone. One {@code afterEach}, in this order:
 * <ol>
 *   <li>if the test failed ({@link ExtensionContext#getExecutionException()} is present), save a
 *       screenshot and stop tracing to a {@code trace.zip}, both under a folder named after the
 *       scenario ({@link #ARTIFACTS_ROOT});</li>
 *   <li>otherwise, stop tracing with no path, which discards it;</li>
 *   <li>close the {@link BrowserContext}, whatever happened above.</li>
 * </ol>
 * Each of those steps is guarded: a problem saving artifacts is attached to the test's own
 * failure as a suppressed exception rather than replacing it (so the real failure is never
 * hidden), and the context is always closed even if artifact capture blew up. See
 * {@link #afterEach(ExtensionContext)}.
 */
public final class BrowserExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);
    private static final String BROWSER_KEY = "sharedBrowser";
    private static final String CONTEXT_KEY = "browserContext";

    /**
     * Where a failed check's screenshot and trace are saved, resolved relative to the current
     * working directory: Surefire forks its JVM with the module directory (here, {@code
     * ui-tests}) as the working directory, so this resolves to {@code ui-tests/target/ui-failures}
     * without needing to know the module's location any other way. Under {@code target/}, so
     * {@code clean} wipes it and it is already git-ignored.
     */
    private static final Path ARTIFACTS_ROOT = Path.of("target", "ui-failures");

    /**
     * Matches the last segment of a parameterized test invocation's unique id, for example
     * {@code ...[test-template-invocation:#2]}, capturing the row number. Used instead of the
     * invocation's own display name (which JUnit resolves with the actual argument text, see
     * {@link ArtifactNames}) to keep row identity without ever touching argument values.
     */
    private static final Pattern INVOCATION_INDEX = Pattern.compile("\\[test-template-invocation:#(\\d+)]$");

    @Override
    public void beforeEach(ExtensionContext context) {
        Browser browser = sharedBrowser(context);
        BrowserContext browserContext = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1280, 800));
        context.getStore(NAMESPACE).put(CONTEXT_KEY, browserContext);
        browserContext.tracing().start(TraceSettings.startOptions());

        Page page = browserContext.newPage();
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof UiTest uiTest) {
            uiTest.page = page;
        } else {
            throw new IllegalStateException(
                    "BrowserExtension expects the test instance to extend UiTest, but got: "
                            + testInstance.getClass());
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        BrowserContext browserContext = context.getStore(NAMESPACE).remove(CONTEXT_KEY, BrowserContext.class);
        if (browserContext == null) {
            return;
        }
        Optional<Throwable> testFailure = context.getExecutionException();
        RuntimeException problem = capture(context, browserContext, testFailure.isPresent());
        problem = closeQuietly(browserContext, problem);

        if (problem == null) {
            return;
        }
        if (testFailure.isPresent()) {
            // The test already failed; attach the capture/close problem to it instead of
            // throwing, so the test's own failure stays the one JUnit reports.
            testFailure.get().addSuppressed(problem);
            return;
        }
        throw problem;
    }

    /** Runs the one artifact step that applies, returning any problem instead of throwing it. */
    private RuntimeException capture(ExtensionContext context, BrowserContext browserContext, boolean testFailed) {
        try {
            if (testFailed) {
                saveFailureArtifacts(context, browserContext);
            } else {
                browserContext.tracing().stop();
            }
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** Closes {@code browserContext}, folding any failure into {@code existingProblem}. */
    private RuntimeException closeQuietly(BrowserContext browserContext, RuntimeException existingProblem) {
        try {
            browserContext.close();
            return existingProblem;
        } catch (RuntimeException closeFailure) {
            if (existingProblem != null) {
                existingProblem.addSuppressed(closeFailure);
                return existingProblem;
            }
            return closeFailure;
        }
    }

    /**
     * Saves the screenshot and the trace for a failed check, under a folder named after its
     * scenario. Each of the two saves is attempted independently: one failing (for example, a
     * page that closed itself) does not stop the other from being tried.
     */
    private void saveFailureArtifacts(ExtensionContext context, BrowserContext browserContext) {
        Path folder = failureFolder(context);
        List<Throwable> problems = new ArrayList<>();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            problems.add(new UncheckedIOException("Could not create " + folder, e));
        }
        try {
            Page page = pageOf(context);
            if (page != null) {
                page.screenshot(new Page.ScreenshotOptions().setPath(folder.resolve("screenshot.png")));
            }
        } catch (RuntimeException e) {
            problems.add(e);
        }
        try {
            browserContext.tracing().stop(new Tracing.StopOptions().setPath(folder.resolve("trace.zip")));
        } catch (RuntimeException e) {
            problems.add(e);
        }
        if (!problems.isEmpty()) {
            RuntimeException failure = new RuntimeException("Could not save failure artifacts to " + folder);
            problems.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static Page pageOf(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        return testInstance instanceof UiTest uiTest ? uiTest.page : null;
    }

    /**
     * {@code ui-tests/target/ui-failures/<TestClass simple name>/<sanitised display
     * name>[-<invocation index>]}. Built only from the class name, the argument-free display
     * name (see {@link ArtifactNames}), and, for a parameterized row, its plain invocation index
     * — never from parameter values, so the password can never end up in a path.
     */
    private Path failureFolder(ExtensionContext context) {
        String className = context.getRequiredTestClass().getSimpleName();
        Matcher invocation = INVOCATION_INDEX.matcher(context.getUniqueId());
        boolean isParameterizedInvocation = invocation.find();
        String displayName = isParameterizedInvocation
                // The invocation's own display name has the resolved argument text baked in
                // (e.g. "Sign in [I_Like_This_Lab]"); its parent, the test-template container,
                // carries the plain @DisplayName (or its default) instead. Verified by
                // experiment against JUnit 5's unique-id/display-name shapes for a
                // @ParameterizedTest (documented here rather than in a committed throwaway test).
                ? context.getParent().map(ExtensionContext::getDisplayName).orElseGet(context::getDisplayName)
                : context.getDisplayName();
        String leaf = ArtifactNames.sanitise(displayName);
        if (isParameterizedInvocation) {
            leaf = leaf + "-" + invocation.group(1);
        }
        return ARTIFACTS_ROOT.resolve(className).resolve(leaf);
    }

    private Browser sharedBrowser(ExtensionContext context) {
        SharedBrowser shared = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(BROWSER_KEY, key -> launchSharedBrowser(), SharedBrowser.class);
        return shared.browser;
    }

    private static SharedBrowser launchSharedBrowser() {
        Playwright playwright = Playwright.create();
        try {
            boolean headless = !Boolean.getBoolean("headed");
            double slowMo = Double.parseDouble(System.getProperty("slowMo", "0"));
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setSlowMo(slowMo));
            return new SharedBrowser(playwright, browser);
        } catch (RuntimeException launchFailure) {
            playwright.close();
            throw new IllegalStateException(
                    "Chromium failed to launch. If the browser binaries are missing, install them once with "
                            + "'./mvnw -N exec:java' from the repository root, then re-run the checks.",
                    launchFailure);
        }
    }

    /**
     * Holds the run-scoped Playwright driver and Chromium browser, closed together at the end of
     * the run. Implements both {@link ExtensionContext.Store.CloseableResource} (what the Store
     * requires) and {@link AutoCloseable} (what newer JUnit Platform versions prefer), so nothing
     * is logged about the older interface being used on its own.
     */
    @SuppressWarnings("deprecation")
    private static final class SharedBrowser implements ExtensionContext.Store.CloseableResource, AutoCloseable {
        private final Playwright playwright;
        private final Browser browser;

        private SharedBrowser(Playwright playwright, Browser browser) {
            this.playwright = playwright;
            this.browser = browser;
        }

        @Override
        public void close() {
            browser.close();
            playwright.close();
        }
    }
}
