package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * The lab's Report screen for one sample. An instance exists only once the screen is ready.
 * Only this class knows the screen's elements. (Minimal for now: the report's contents are
 * read in a later slice.)
 */
public final class ReportPage {

    private final Locator heading;

    private ReportPage(Page page) {
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Report").setExact(true).setLevel(1));
    }

    /**
     * Returns the Report screen once its "Report" heading is visible. Used by the Page Objects
     * whose actions land on this screen. Waits with Playwright's auto-waiting (default timeout).
     */
    static ReportPage ready(Page page) {
        ReportPage report = new ReportPage(page);
        report.heading.waitFor();
        return report;
    }
}
