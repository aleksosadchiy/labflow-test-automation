package com.labflow.ui.tests.framework.preflight;

import com.labflow.client.preflight.LabPreflight;
import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.BrowserChoice;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the lab pre-flight once, when the JUnit Platform opens its session, before any screen
 * check is discovered or executed. If the lab is down or rejects the key, the exception aborts
 * the whole run with one clear message instead of every screen check failing on its own, one
 * navigation at a time.
 *
 * <p>Copied from {@code api-tests}' {@code com.labflow.tests.framework.preflight.LabPreflightListener}: a
 * session listener runs once per run, unlike a JUnit extension registered on the base class
 * (which would run once per test class), and {@code ServiceLoader} registration
 * ({@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}) is per module
 * classpath, so {@code ui-tests} needs its own copy to keep {@code common} and {@code api-client}
 * running without a lab.
 *
 * <p>The settings (environment and browser) are resolved first, so an unknown value stops the
 * run before any check with the message from {@link LabConfig} or {@link BrowserChoice}. Then
 * one INFO banner line shows what the run targets (the key is always {@code ***}), and only
 * then is the lab probed.
 */
public class UiPreflightListener implements LauncherSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(UiPreflightListener.class);

    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        runOnce(ANNOUNCED, () -> {
            LabConfig config = LabConfig.fromEnvironment();
            BrowserChoice browser = BrowserChoice.fromEnvironment();
            LOG.info(banner(config, browser));
            LabPreflight.check(config);
        });
    }

    /**
     * Runs {@code action} only for the first caller of a guard. Self-tests start nested launcher
     * sessions in the same JVM and {@code ServiceLoader} registers this listener for each, so
     * without the guard the banner and the lab probe would repeat once per nested session.
     */
    public static void runOnce(AtomicBoolean guard, Runnable action) {
        if (guard.compareAndSet(false, true)) {
            action.run();
        }
    }

    /** The one-line settings banner. The key is never read: it is always shown as {@code ***}. */
    public static String banner(LabConfig config, BrowserChoice browser) {
        return "Lab run: environment=" + config.environment() + " address=" + config.baseUrl()
                + " key=*** browser=" + browser;
    }
}
