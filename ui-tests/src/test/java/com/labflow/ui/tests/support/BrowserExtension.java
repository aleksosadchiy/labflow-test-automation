package com.labflow.ui.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
 * debugging). Tracing and other failure artifacts are not part of this extension yet (Slice 6).
 */
public final class BrowserExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);
    private static final String BROWSER_KEY = "sharedBrowser";
    private static final String CONTEXT_KEY = "browserContext";

    @Override
    public void beforeEach(ExtensionContext context) {
        Browser browser = sharedBrowser(context);
        BrowserContext browserContext = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1280, 800));
        context.getStore(NAMESPACE).put(CONTEXT_KEY, browserContext);

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
        if (browserContext != null) {
            browserContext.close();
        }
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
