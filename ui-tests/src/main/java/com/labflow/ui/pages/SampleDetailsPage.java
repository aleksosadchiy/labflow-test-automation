package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.WorkflowStage;
import java.util.List;

/**
 * The lab's Sample Details screen for one sample. An instance exists only once the screen is
 * ready: either the sample's details are shown, or (for an unknown id, via
 * {@link #openExpectingNotFound}) the "not found" message is. Only this class knows the screen's
 * address and elements.
 */
public final class SampleDetailsPage {

    private static final String PATH = "/samples/";

    private final Page page;
    private final Locator details;
    private final Locator sampleId;
    private final Locator patientName;
    private final Locator currentStatus;
    private final Locator stageItems;
    private final Locator advanceButton;
    private final Locator viewReportLink;
    private final Locator notFoundMessage;

    private SampleDetailsPage(Page page) {
        this.page = page;
        this.details = page.locator("#sample-details");
        this.sampleId = page.locator("#sample-id");
        this.patientName = page.locator("#patient-name");
        this.currentStatus = page.locator("#current-status");
        this.stageItems = page.locator("#stage-list li");
        this.advanceButton = page.locator("#advance-button");
        this.viewReportLink = page.locator("#view-report-link");
        this.notFoundMessage = page.locator("#not-found-message");
    }

    /** Opens the Details screen of the given sample and returns once its details are shown. */
    public static SampleDetailsPage open(Page page, LabConfig config, String sampleId) {
        page.navigate(config.baseUrl() + PATH + sampleId);
        return ready(page);
    }

    /**
     * Opens the Details screen for an id the lab is expected not to know. The lab then shows a
     * "not found" message instead of details, so this returns once that message is visible.
     */
    public static SampleDetailsPage openExpectingNotFound(Page page, LabConfig config, String sampleId) {
        page.navigate(config.baseUrl() + PATH + sampleId);
        SampleDetailsPage detailsPage = new SampleDetailsPage(page);
        detailsPage.notFoundMessage.waitFor();
        return detailsPage;
    }

    /**
     * Returns the Details screen once the sample's details are visible. Used by the Page Objects
     * whose actions land on this screen. Waits with Playwright's auto-waiting (default timeout).
     */
    static SampleDetailsPage ready(Page page) {
        SampleDetailsPage detailsPage = new SampleDetailsPage(page);
        detailsPage.details.waitFor();
        return detailsPage;
    }

    /** The id of the sample shown on this screen. */
    public String sampleId() {
        return sampleId.textContent().strip();
    }

    /** The name of the sample's patient, as shown on this screen. */
    public String patientName() {
        return patientName.textContent().strip();
    }

    /** The sample's current stage, as shown in "Current Status". */
    public WorkflowStage currentStatus() {
        return StatusText.toStage(currentStatus.textContent());
    }

    /** Every stage in the screen's stage list, in the order shown, with how far along each is. */
    public List<StageView> stages() {
        return stageItems.all().stream()
                .map(item -> new StageView(StatusText.toStage(item.textContent()),
                        StageState.fromCssClass(item.getAttribute("class"))))
                .toList();
    }

    /**
     * Moves the sample to its next stage with the Advance button. The lab posts and then shows
     * this same screen again, so this waits until "Current Status" shows the next stage (polling
     * managed by Playwright, default timeout) and returns the refreshed screen.
     *
     * @throws IllegalStateException if the sample is already at the final stage; check
     *                               {@link #canAdvance()} first
     */
    public SampleDetailsPage advance() {
        WorkflowStage from = currentStatus();
        WorkflowStage to = from.next().orElseThrow(() -> new IllegalStateException(
                "Sample " + sampleId() + " is already at the final stage " + from + " and cannot be advanced"));
        advanceButton.click();
        page.waitForCondition(() -> to.name().equals(currentStatus.textContent().strip()));
        return ready(page);
    }

    /**
     * Whether the Advance button is shown. Does not wait: this screen is already ready, so an
     * absent button means the lab does not offer it.
     */
    public boolean canAdvance() {
        return advanceButton.isVisible();
    }

    /**
     * Whether the View Report link is shown. Does not wait: this screen is already ready, so an
     * absent link means the lab does not offer it.
     */
    public boolean canViewReport() {
        return viewReportLink.isVisible();
    }

    /** Follows the View Report link and returns once the Report screen is ready. */
    public ReportPage openReport() {
        viewReportLink.click();
        return ReportPage.ready(page);
    }

    /** The "not found" message shown instead of details for an id the lab does not know. */
    public String notFoundMessage() {
        return notFoundMessage.textContent().strip();
    }
}
