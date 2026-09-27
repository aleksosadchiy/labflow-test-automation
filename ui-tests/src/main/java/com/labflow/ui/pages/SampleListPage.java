package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * The lab's Sample List screen: what a person sees and does there. An instance exists only
 * once the screen is ready, so everything read from it reflects a loaded screen.
 */
public final class SampleListPage {

    private final Locator heading;

    private SampleListPage(Page page) {
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Samples").setExact(true).setLevel(1));
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
