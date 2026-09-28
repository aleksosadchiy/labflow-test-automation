package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.labflow.common.config.LabConfig;

/**
 * The lab's Create Sample screen: what a person sees and does there. An instance exists only
 * once the form is ready. Only this class knows the screen's address and elements.
 */
public final class CreateSamplePage {

    private static final String PATH = "/samples/new";

    private final Page page;
    private final Locator patientIdField;
    private final Locator sampleTypeField;
    private final Locator createButton;
    private final Locator errorMessage;

    private CreateSamplePage(Page page) {
        this.page = page;
        this.patientIdField = page.locator("#patientId");
        this.sampleTypeField = page.locator("#sampleType");
        // "Create Sample" is also the screen's heading and the list's link text; the BUTTON role
        // with the exact name picks the submit button only (it has no id).
        this.createButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Create Sample").setExact(true));
        this.errorMessage = page.locator("#error-message");
    }

    /** Opens the Create Sample screen while signed in and returns once its form is ready. */
    public static CreateSamplePage open(Page page, LabConfig config) {
        page.navigate(config.baseUrl() + PATH);
        return ready(page);
    }

    /**
     * Returns the Create Sample screen once its form is ready. Used by the Page Objects whose
     * actions land on this screen. Waits with Playwright's auto-waiting (default timeout).
     */
    static CreateSamplePage ready(Page page) {
        CreateSamplePage createSample = new CreateSamplePage(page);
        createSample.patientIdField.waitFor();
        return createSample;
    }

    /**
     * Registers a sample that the lab is expected to accept. The lab then shows the new
     * sample's Details screen, so this returns once that screen is ready.
     */
    public SampleDetailsPage createSample(String patientId, String sampleTypeText) {
        submit(patientId, sampleTypeText);
        return SampleDetailsPage.ready(page);
    }

    /**
     * Submits a sample that the lab is expected to refuse, and returns once the screen's error
     * message is visible.
     */
    public CreateSamplePage createSampleExpectingError(String patientId, String sampleTypeText) {
        submit(patientId, sampleTypeText);
        errorMessage.waitFor();
        return this;
    }

    /** The error message currently shown on the Create Sample screen. */
    public String errorMessage() {
        return errorMessage.textContent().strip();
    }

    private void submit(String patientId, String sampleTypeText) {
        patientIdField.fill(patientId);
        sampleTypeField.fill(sampleTypeText);
        createButton.click();
    }
}
