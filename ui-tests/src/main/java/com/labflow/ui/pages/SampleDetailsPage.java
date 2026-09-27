package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.labflow.common.config.LabConfig;

/**
 * The lab's Sample Details screen for one sample. An instance exists only once the sample's
 * details are shown. Only this class knows the screen's address.
 */
public final class SampleDetailsPage {

    private static final String PATH = "/samples/";

    private final Locator details;
    private final Locator sampleId;

    private SampleDetailsPage(Page page) {
        this.details = page.locator("#sample-details");
        this.sampleId = page.locator("#sample-id");
    }

    /** Opens the Details screen of the given sample and returns once its details are shown. */
    public static SampleDetailsPage open(Page page, LabConfig config, String sampleId) {
        page.navigate(config.baseUrl() + PATH + sampleId);
        return ready(page);
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
}
