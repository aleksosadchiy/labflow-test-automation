package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.labflow.common.config.LabConfig;

/**
 * The lab's Sample List screen: what a person sees and does there. An instance exists only
 * once the screen is ready, so everything read from it reflects a loaded screen. Only this
 * class knows the screen's address.
 */
public final class SampleListPage {

    private static final String PATH = "/samples";

    private final Locator heading;

    private SampleListPage(Page page) {
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Samples").setExact(true).setLevel(1));
    }

    /** Opens the Sample List while signed in and returns once the screen is ready. */
    public static SampleListPage open(Page page, LabConfig config) {
        page.navigate(config.baseUrl() + PATH);
        return ready(page);
    }

    /**
     * Opens the Sample List while signed out. The lab sends the visitor to Sign In instead, so
     * this returns the Sign In screen once its form is ready.
     */
    public static LoginPage openExpectingSignIn(Page page, LabConfig config) {
        page.navigate(config.baseUrl() + PATH);
        return LoginPage.ready(page);
    }

    /**
     * Returns the Sample List once its "Samples" heading is visible. Used by the Page Objects
     * whose actions land on this screen. Waits with Playwright's auto-waiting (default timeout).
     */
    static SampleListPage ready(Page page) {
        SampleListPage sampleList = new SampleListPage(page);
        sampleList.heading.waitFor();
        return sampleList;
    }

    /** Whether the Sample List screen is currently displayed. */
    public boolean isShown() {
        return heading.isVisible();
    }
}
