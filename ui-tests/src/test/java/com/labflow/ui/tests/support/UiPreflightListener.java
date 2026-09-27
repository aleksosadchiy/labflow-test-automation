package com.labflow.ui.tests.support;

import com.labflow.client.preflight.LabPreflight;
import com.labflow.common.config.LabConfig;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Runs the lab pre-flight once, when the JUnit Platform opens its session, before any screen
 * check is discovered or executed. If the lab is down or rejects the key, the exception aborts
 * the whole run with one clear message instead of every screen check failing on its own, one
 * navigation at a time.
 *
 * <p>Copied from {@code api-tests}' {@code com.labflow.tests.support.LabPreflightListener}: a
 * session listener runs once per run, unlike a JUnit extension registered on the base class
 * (which would run once per test class), and {@code ServiceLoader} registration
 * ({@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}) is per module
 * classpath, so {@code ui-tests} needs its own copy to keep {@code common} and {@code api-client}
 * running without a lab.
 */
public class UiPreflightListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        LabPreflight.check(LabConfig.fromEnvironment());
    }
}
