package com.labflow.ui.recording;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.BrowserChoice;
import com.labflow.ui.config.UiCredentials;
import com.labflow.ui.pages.LoginPage;
import com.labflow.ui.pages.SampleListPage;
import com.labflow.ui.tests.framework.browser.BrowserPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidence for FS 2.9 / tech spec §2.5 on the real lab: after a real sign-in with tracing on
 * (the same options the scenario hooks use) and one more recorded action, the real
 * password appears in no entry of the saved trace: not in the action log, not in the DOM
 * snapshots (which record {@code type=password} values), not in the network log.
 *
 * <p>Needs the lab running, like every other test in this module (the pre-flight checks it).
 *
 * @spec: 004-ui-automation-page-objects
 */
class SignInTraceTest {

    @TempDir
    Path tempDir;

    @Test
    // @regression
    @DisplayName("A saved trace of a real sign-in does not contain the password")
    void signInTraceHoldsNoPassword() throws IOException {
        LabConfig config = LabConfig.fromEnvironment();
        UiCredentials credentials = UiCredentials.fromEnvironment();
        Path zip = tempDir.resolve("trace.zip");

        try (Playwright playwright = Playwright.create();
             Browser browser = BrowserPool.browserType(playwright, BrowserChoice.fromEnvironment()).launch();
             BrowserContext context = browser.newContext()) {
            context.tracing().start(TraceSettings.startOptions());
            Page page = context.newPage();

            LoginPage.open(page, config).signIn(credentials);
            // One more recorded action after the pause, so the trace holds a DOM snapshot taken
            // once recording has resumed.
            boolean listShown = SampleListPage.open(page, config).isShown();

            context.tracing().stop(new Tracing.StopOptions().setPath(zip));
            assertThat(listShown).as("signed in and on the Sample List").isTrue();
        }

        String samplesUrl = config.baseUrl() + "/samples";
        boolean recordedAfterResume = false;
        List<String> leaking = new ArrayList<>();
        String password = credentials.password().reveal();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                // ISO-8859-1 maps every byte to a char, so binary entries are searched too.
                String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                recordedAfterResume |= text.contains(samplesUrl);
                if (text.contains(password)) {
                    leaking.add(entry.getName());
                }
            }
        }

        assertThat(recordedAfterResume).as("the trace recorded the reopening of the Sample List").isTrue();
        assertThat(leaking).as("trace entries containing the real password").isEmpty();
    }
}
