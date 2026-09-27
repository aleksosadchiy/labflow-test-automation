package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.UiCredentials;

/**
 * The lab's Sign In screen: what a person sees and does there. Only this class knows the
 * screen's address and elements; checks talk to it in business terms.
 */
public final class LoginPage {

    private static final String PATH = "/login";

    private final Page page;
    private final Locator heading;
    private final Locator usernameField;
    private final Locator passwordField;
    private final Locator signInButton;

    private LoginPage(Page page) {
        this.page = page;
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Sign In").setExact(true).setLevel(1));
        this.usernameField = page.locator("#username");
        this.passwordField = page.locator("#password");
        // The button has no id; its role and visible label identify it (and pin the label).
        this.signInButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In"));
    }

    /** Opens the Sign In screen and returns once its form is ready. */
    public static LoginPage open(Page page, LabConfig config) {
        page.navigate(config.baseUrl() + PATH);
        LoginPage loginPage = new LoginPage(page);
        loginPage.usernameField.waitFor();
        return loginPage;
    }

    /** Signs in with the given details and returns once the Sample List screen is ready. */
    public SampleListPage signIn(UiCredentials credentials) {
        usernameField.fill(credentials.username());
        passwordField.fill(credentials.password().reveal());
        signInButton.click();
        return SampleListPage.ready(page);
    }

    /** Whether the Sign In screen is currently displayed. */
    public boolean isShown() {
        return heading.isVisible();
    }
}
