package com.labflow.ui.tests.framework.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.labflow.ui.config.BrowserChoice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The browsers of the screen scenarios: one {@link Playwright} driver and one {@link Browser} per
 * worker thread, created on that thread's first scenario and reused by every later one. The
 * reasons: a Playwright instance is not thread-safe, and a driver per scenario would cost one to
 * two seconds each.
 *
 * <p>Every browser is also remembered in one list, and {@link #closeAll()} (called once, from
 * the global {@code @AfterAll} hook after the last scenario) closes them all, browser first, then
 * its driver, collecting failures instead of stopping at the first. The browser comes from the
 * settings {@link BrowserChoice} ({@code -Dbrowser}, {@code BROWSER}), {@code -Dheaded} and
 * {@code -DslowMo}.
 */
public final class BrowserPool {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserPool.class);

    private static final ThreadLocal<Handle> PER_THREAD = new ThreadLocal<>();
    private static final List<Handle> ALL = Collections.synchronizedList(new ArrayList<>());

    private BrowserPool() {
    }

    /** The browser of the calling worker thread; launched on the thread's first call. */
    public static Browser browserForThisThread() {
        Handle handle = PER_THREAD.get();
        if (handle == null) {
            handle = launch();
            ALL.add(handle);
            PER_THREAD.set(handle);
        }
        return handle.browser;
    }

    /** Closes every browser and driver the run started; safe to call when none was started. */
    public static void closeAll() {
        List<Handle> toClose;
        synchronized (ALL) {
            toClose = new ArrayList<>(ALL);
            ALL.clear();
        }
        List<RuntimeException> failures = new ArrayList<>();
        for (Handle handle : toClose) {
            try {
                handle.close();
            } catch (RuntimeException e) {
                failures.add(e);
            }
        }
        LOG.info("Browsers closed: {}", toClose.size());
        if (!failures.isEmpty()) {
            RuntimeException failure = new IllegalStateException(
                    failures.size() + " of " + toClose.size() + " browsers did not close cleanly");
            failures.forEach(failure::addSuppressed);
            throw failure;
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

    private static Handle launch() {
        BrowserChoice choice = BrowserChoice.fromEnvironment();
        Playwright playwright = Playwright.create();
        try {
            boolean headless = !Boolean.getBoolean("headed");
            double slowMo = Double.parseDouble(System.getProperty("slowMo", "0"));
            Browser browser = browserType(playwright, choice).launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setSlowMo(slowMo));
            LOG.info("Browser started for worker {}", Thread.currentThread().getName());
            return new Handle(playwright, browser);
        } catch (RuntimeException launchFailure) {
            playwright.close();
            throw new IllegalStateException(
                    choice + " failed to launch. If the browser binaries are missing, install them once with '"
                            + choice.installCommand() + "' from the repository root, then re-run the checks.",
                    launchFailure);
        }
    }

    private static final class Handle {
        private final Playwright playwright;
        private final Browser browser;

        private Handle(Playwright playwright, Browser browser) {
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
}
