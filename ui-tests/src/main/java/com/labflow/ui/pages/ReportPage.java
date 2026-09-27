package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.labflow.common.config.LabConfig;

/**
 * The lab's Report screen for one sample. An instance exists only once the screen is ready:
 * either the report's details are shown, or (for a sample that has not reached the final stage,
 * via {@link #openExpectingNotReady}) the "not ready" message is. Only this class knows the
 * screen's address and elements.
 */
public final class ReportPage {

    private static final String PATH_PREFIX = "/samples/";
    private static final String PATH_SUFFIX = "/report";

    private final Locator details;
    private final Locator sampleId;
    private final Locator testName;
    private final Locator resultValue;
    private final Locator generatedAt;
    private final Locator notReadyMessage;

    private ReportPage(Page page) {
        this.details = page.locator("#report-details");
        this.sampleId = page.locator("#report-sample-id");
        this.testName = page.locator("#report-test-name");
        this.resultValue = page.locator("#report-result-value");
        this.generatedAt = page.locator("#report-generated-at");
        this.notReadyMessage = page.locator("#not-ready-message");
    }

    /** Opens the Report screen of the given sample and returns once the report's details are shown. */
    public static ReportPage open(Page page, LabConfig config, String sampleId) {
        page.navigate(reportUrl(config, sampleId));
        return ready(page);
    }

    /**
     * Opens the Report screen of a sample that is expected not to have reached the final stage.
     * The lab then shows a "not ready" message instead of details, so this returns once that
     * message is visible.
     */
    public static ReportPage openExpectingNotReady(Page page, LabConfig config, String sampleId) {
        page.navigate(reportUrl(config, sampleId));
        ReportPage report = new ReportPage(page);
        report.notReadyMessage.waitFor();
        return report;
    }

    /**
     * Returns the Report screen once the report's details are visible. Used by the Page Objects
     * whose actions land on this screen (the lab offers View Report only for a finished sample).
     * Waits with Playwright's auto-waiting (default timeout).
     */
    static ReportPage ready(Page page) {
        ReportPage report = new ReportPage(page);
        report.details.waitFor();
        return report;
    }

    /** The id of the sample this report belongs to, as shown on the screen. */
    public String sampleId() {
        return sampleId.textContent().strip();
    }

    /** The test name, as shown on the screen. */
    public String testName() {
        return testName.textContent().strip();
    }

    /** The result value, as shown on the screen. */
    public String resultValue() {
        return resultValue.textContent().strip();
    }

    /** When the report was generated, as the displayed text (not parsed). */
    public String generatedAt() {
        return generatedAt.textContent().strip();
    }

    /** The "not ready" message shown instead of details for a sample not yet at the final stage. */
    public String notReadyMessage() {
        return notReadyMessage.textContent().strip();
    }

    private static String reportUrl(LabConfig config, String sampleId) {
        return config.baseUrl() + PATH_PREFIX + sampleId + PATH_SUFFIX;
    }
}
