package com.labflow.ui.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.labflow.ui.config.BrowserChoice;
import com.labflow.ui.recording.TraceSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the browser lifecycle for every screen check.
 *
 * <p><b>One browser per worker thread.</b> A {@link BrowserHandle} (one {@link Playwright} driver
 * plus one {@link Browser} of the chosen type, {@link BrowserChoice}: {@code -Dbrowser}, then
 * {@code BROWSER}, then Chromium) belongs to one JUnit worker thread. It is created lazily on that
 * thread's first screen check ({@code beforeEach} runs on the worker thread), kept in a
 * {@link ThreadLocal}, and reused by every later check that thread runs. So a run never has more
 * browsers than it has worker threads ({@code checksAtOnce}), and a sequential run has exactly one.
 * Every handle is also registered in one {@link Handles} object in the <b>root</b>
 * {@link ExtensionContext.Store}; JUnit closes it once, after the last check, from whichever thread
 * closes the root store, and it closes every handle (browser first, then Playwright), collecting
 * failures instead of stopping at the first. There is no {@code @BeforeAll}/{@code @AfterAll} to
 * write and no static field to leak state between test classes or between engine runs.
 *
 * <p><b>Why this shape.</b> Playwright for Java is not thread-safe <em>per Playwright instance</em>:
 * two threads must not call into the same {@link Playwright} (or anything created from it) at the
 * same time. One shared instance, the design before parallel execution, would break as soon as two
 * checks run at once. The opposite extreme, a Playwright per check, is safe but starts a Node driver
 * process for every check (about 1 to 2 seconds each), which would cancel the speed-up. One per
 * worker thread is the middle: safe, and the driver start-up is paid at most once per thread.
 *
 * <p><b>The time limit does not break this.</b> The run uses {@code SEPARATE_THREAD} timeouts, so a
 * check body runs on a helper thread while its worker thread blocks waiting for it. The worker does
 * not run another check meanwhile (it blocks in the timeout's {@code Future.get}, not in a
 * fork-join join, so it does not steal work), so each Playwright is still used by one thread at a
 * time. A body abandoned at its time limit can still touch its own worker's handle, but that noise
 * stays on that one worker instead of reaching the whole run.
 *
 * <p>Each test method then gets its own fresh {@link BrowserContext} (fixed 1280x800 viewport)
 * and {@link Page}, assigned directly to the {@link UiTest} instance's {@code page} field before
 * the test body runs, and closed again once it finishes. That fresh context is each check's own
 * separate browser window: checks never share cookies, local storage or navigation history.
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
 *       browser and the scenario ({@link #ARTIFACTS_ROOT}{@code /<browser>/<TestClass>/<scenario>});</li>
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
    private static final String HANDLES_KEY = "browserHandles";
    private static final Logger LOG = LoggerFactory.getLogger(BrowserExtension.class);
    private static final String CONTEXT_KEY = "browserContext";

    /**
     * Where a failed check's screenshot and trace are saved, resolved relative to the current
     * working directory: Surefire forks its JVM with the module directory (here, {@code
     * ui-tests}) as the working directory, so this resolves to {@code ui-tests/target/ui-failures}
     * without needing to know the module's location any other way. Under {@code target/}, so
     * {@code clean} wipes it and it is already git-ignored. Below it, one folder per browser, so
     * a run in another browser never overwrites this one's evidence.
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
        Browser browser = handles(context).handleForThisThread().browser;
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
     * {@code ui-tests/target/ui-failures/<browser>/<TestClass simple name>/<sanitised display
     * name>[-<invocation index>]}. Built only from the browser's name, the class name, the argument-free display
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
        return ARTIFACTS_ROOT.resolve(BrowserChoice.fromEnvironment().displayName()).resolve(className).resolve(leaf);
    }

    private static Handles handles(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(HANDLES_KEY, key -> new Handles(), Handles.class);
    }

    private static BrowserHandle launch() {
        BrowserChoice choice = BrowserChoice.fromEnvironment();
        Playwright playwright = Playwright.create();
        try {
            boolean headless = !Boolean.getBoolean("headed");
            double slowMo = Double.parseDouble(System.getProperty("slowMo", "0"));
            Browser browser = browserType(playwright, choice).launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setSlowMo(slowMo));
            LOG.info("Browser started for worker {}", Thread.currentThread().getName());
            return new BrowserHandle(playwright, browser);
        } catch (RuntimeException launchFailure) {
            playwright.close();
            throw new IllegalStateException(
                    choice + " failed to launch. If the browser binaries are missing, install them once with '"
                            + choice.installCommand() + "' from the repository root, then re-run the checks.",
                    launchFailure);
        }
    }

    /**
     * The Playwright engine for {@code choice}. Public so checks that launch their own browser
     * (the recording unit tests) honour {@code BROWSER} too, instead of assuming Chromium.
     */
    public static BrowserType browserType(Playwright playwright, BrowserChoice choice) {
        return switch (choice) {
            case CHROMIUM -> playwright.chromium();
            case WEBKIT -> playwright.webkit();
        };
    }

    /** One Playwright driver and its browser, used by one worker thread. */
    private static final class BrowserHandle {
        private final Playwright playwright;
        private final Browser browser;

        private BrowserHandle(Playwright playwright, Browser browser) {
            this.playwright = playwright;
            this.browser = browser;
        }

        /** Browser first, then the driver it runs under. */
        private void close() {
            try {
                browser.close();
            } finally {
                playwright.close();
            }
        }
    }

    /**
     * The run's browsers: the {@link ThreadLocal} that gives each worker thread its own handle, and
     * the synchronized list that remembers every handle so all of them close once at the end of the
     * run. Both live in this one object, which lives in the root store, so a new engine run gets
     * fresh ones and a handle closed at the end of one run is never handed out by the next.
     * Implements both {@link ExtensionContext.Store.CloseableResource} (what the Store requires)
     * and {@link AutoCloseable} (what newer JUnit Platform versions prefer), so nothing is logged
     * about the older interface being used on its own.
     */
    @SuppressWarnings("deprecation")
    private static final class Handles implements ExtensionContext.Store.CloseableResource, AutoCloseable {
        private final ThreadLocal<BrowserHandle> perThread = new ThreadLocal<>();
        private final List<BrowserHandle> all = Collections.synchronizedList(new ArrayList<>());

        private BrowserHandle handleForThisThread() {
            BrowserHandle handle = perThread.get();
            if (handle == null) {
                handle = launch();
                all.add(handle);
                perThread.set(handle);
            }
            return handle;
        }

        @Override
        public void close() {
            List<BrowserHandle> toClose;
            synchronized (all) {
                toClose = new ArrayList<>(all);
                all.clear();
            }
            List<RuntimeException> failures = new ArrayList<>();
            for (BrowserHandle handle : toClose) {
                try {
                    handle.close();
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            }
            if (!failures.isEmpty()) {
                RuntimeException failure = new IllegalStateException(
                        failures.size() + " of " + toClose.size() + " browsers did not close cleanly");
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        }
    }
}
