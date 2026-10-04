package com.labflow.tests.support;

import com.labflow.client.preflight.LabPreflight;
import com.labflow.common.config.LabConfig;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the lab pre-flight once, when the JUnit Platform opens its session, before any check is
 * discovered or executed. If the lab is down or rejects the key, the exception aborts the whole
 * run with one clear message instead of dozens of connection errors.
 *
 * <p>Why a session listener and not a JUnit extension on the base class: an extension runs once
 * per test class, so a dead lab would report the same failure once per class. A session
 * listener runs once per run. It is registered by {@code ServiceLoader}
 * ({@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}) and lives
 * only in {@code api-tests}, so the {@code common} and {@code api-client} self-tests still run
 * without a lab.
 *
 * <p>The settings are resolved first, so an unknown environment stops the run before any check
 * with the message from {@link LabConfig}. Then one INFO banner line shows what the run
 * targets (the key is always {@code ***}), and only then is the lab probed.
 */
public class LabPreflightListener implements LauncherSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(LabPreflightListener.class);

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        LabConfig config = LabConfig.fromEnvironment();
        LOG.info(banner(config));
        LabPreflight.check(config);
    }

    /** The one-line settings banner. The key is never read: it is always shown as {@code ***}. */
    static String banner(LabConfig config) {
        return "Lab run: environment=" + config.environment() + " address=" + config.baseUrl() + " key=***";
    }
}
