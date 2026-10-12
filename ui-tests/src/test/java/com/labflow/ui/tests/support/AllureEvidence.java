package com.labflow.ui.tests.support;

import io.qameta.allure.Allure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies a failed screen check's evidence (the screenshot and the Playwright trace that {@link
 * BrowserExtension} saved) into its Allure report, so a reader of the report does not have to
 * look for the files under {@code target/ui-failures}.
 *
 * <p>The files are attached as they are. The trace is already safe to share: {@code
 * SignInTraceTest} checks that the password of a real sign-in appears nowhere in a saved trace.
 *
 * <p>Does nothing when Allure has no running check on this thread, which is the case for the
 * self-tests that run a throwaway check through JUnit's test kit. A file that cannot be attached
 * is logged and skipped: the report is a convenience and must never turn a check red.
 */
final class AllureEvidence {

    private static final Logger LOG = LoggerFactory.getLogger(AllureEvidence.class);

    private AllureEvidence() {
    }

    static void attach(Path failureFolder) {
        if (Allure.getLifecycle().getCurrentTestCase().isEmpty()) {
            return;
        }
        attachIfPresent("Screenshot at the moment of failure", "image/png", "png", failureFolder.resolve("screenshot.png"));
        attachIfPresent("Playwright trace (open at trace.playwright.dev)", "application/zip", "zip", failureFolder.resolve("trace.zip"));
    }

    private static void attachIfPresent(String name, String mediaType, String extension, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream content = Files.newInputStream(file)) {
            Allure.addAttachment(name, mediaType, content, extension);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not attach {} to the Allure report: {}", file, e.toString());
        }
    }
}
