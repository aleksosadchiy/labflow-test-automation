package com.labflow.ui.tests.support;

import com.microsoft.playwright.Page;
import com.labflow.client.LabClient;
import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.UiCredentials;
import com.labflow.ui.pages.LoginPage;
import com.labflow.ui.pages.SampleListPage;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for every screen check, mirroring {@code api-tests}' {@code LabTest}. It holds the
 * lab client and configuration for the lab under test (env vars / system properties, resolved
 * once per test instance) and the sign-in credentials, plus the Playwright {@link Page} that
 * {@link BrowserExtension} sets fresh before each test runs. There is no static or mutable
 * shared state, so checks stay independent of each other and safe to run in any order.
 */
@ExtendWith({ScenarioNameExtension.class, BrowserExtension.class})
public abstract class UiTest {

    protected final LabConfig config = LabConfig.fromEnvironment();
    protected final UiCredentials credentials = UiCredentials.fromEnvironment();
    protected final LabClient lab = LabClient.create(config);

    /** Set by {@link BrowserExtension} before each test; a fresh page for every test method. */
    protected Page page;

    /** Signs in with the lab's configured credentials and lands on the Sample List screen. */
    protected SampleListPage signedIn() {
        return LoginPage.open(page, config).signIn(credentials);
    }
}
