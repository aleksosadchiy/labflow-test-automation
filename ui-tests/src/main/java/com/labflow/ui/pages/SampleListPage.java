package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.labflow.common.config.LabConfig;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The lab's Sample List screen: what a person sees and does there. An instance exists only
 * once the screen is ready, so everything read from it reflects a loaded screen. Only this
 * class knows the screen's address.
 */
public final class SampleListPage {

    private static final String PATH = "/samples";

    private final Page page;
    private final Locator heading;
    private final Locator createSampleLink;
    private final Locator sampleRows;

    private SampleListPage(Page page) {
        this.page = page;
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Samples").setExact(true).setLevel(1));
        this.createSampleLink = page.locator("#create-sample-link");
        this.sampleRows = page.locator("#sample-table tbody tr");
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

    /** Follows the "Create Sample" link and returns once the Create Sample form is ready. */
    public CreateSamplePage startNewSample() {
        createSampleLink.click();
        return CreateSamplePage.ready(page);
    }

    /**
     * The row of the given sample, or empty if the list does not show it. The row is found by
     * its id in the first column, never by position, because the list is shared and grows.
     * The list is rendered by the server and is complete once this screen is ready, so this
     * reads it as it is and does not wait for a row to appear.
     */
    public Optional<SampleRow> rowFor(String sampleId) {
        // Playwright evaluates the pattern as a JavaScript regex, which has no \Q...\E quoting,
        // so the id's special characters are escaped one by one.
        String literalId = sampleId.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
        Pattern exactId = Pattern.compile("^\\s*" + literalId + "\\s*$");
        Locator idCell = page.locator("td:first-child").filter(new Locator.FilterOptions().setHasText(exactId));
        Locator matches = sampleRows.filter(new Locator.FilterOptions().setHas(idCell));
        int count = matches.count();
        if (count == 0) {
            return Optional.empty();
        }
        if (count > 1) {
            throw new IllegalStateException("The Sample List shows " + count + " rows for sample " + sampleId);
        }
        List<String> cells = matches.locator("td").allTextContents();
        return Optional.of(new SampleRow(cells.get(0).strip(), cells.get(1).strip(), StatusText.toStage(cells.get(2))));
    }
}
