package com.labflow.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.labflow.common.config.LabConfig;
import com.labflow.ui.config.UiCredentials;
import com.labflow.ui.recording.RecordingPause;
import java.util.concurrent.atomic.AtomicReference;

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
    private final Locator errorMessage;

    private LoginPage(Page page) {
        this.page = page;
        this.heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Sign In").setExact(true).setLevel(1));
        this.usernameField = page.locator("#username");
        this.passwordField = page.locator("#password");
        // The button has no id; its role and visible label identify it (and pin the label).
        this.signInButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In"));
        this.errorMessage = page.locator("#error-message");
    }

    /** Opens the Sign In screen and returns once its form is ready. */
    public static LoginPage open(Page page, LabConfig config) {
        page.navigate(config.baseUrl() + PATH);
        return ready(page);
    }

    /**
     * Returns the Sign In screen once its form is ready. Used by the Page Objects whose actions
     * land on this screen (for example, a signed-out visit to another screen). Waits with
     * Playwright's auto-waiting (default timeout).
     */
    static LoginPage ready(Page page) {
        LoginPage loginPage = new LoginPage(page);
        loginPage.usernameField.waitFor();
        return loginPage;
    }

    /** Signs in with the given details and returns once the Sample List screen is ready. */
    public SampleListPage signIn(UiCredentials credentials) {
        usernameField.fill(credentials.username());
        // The real password must not end up in a saved trace (FS 2.9). A trace can carry it in
        // three places: the action log (fill() values verbatim), the DOM snapshots (they record
        // the value of a type=password field, so the snapshot for the Sign In click would hold
        // it), and the network log (the form's POST body). So the pause covers the fill, the
        // click and the wait for the Sample List: RecordingPause discards the trace so far and
        // starts a new one only once the form holding the password is gone (tech spec §2.5).
        // Trade-off: for a check that signs in, the saved trace starts at the Sample List.
        AtomicReference<SampleListPage> landedOn = new AtomicReference<>();
        RecordingPause.around(page, () -> {
            passwordField.fill(credentials.password().reveal());
            signInButton.click();
            landedOn.set(SampleListPage.ready(page));
        });
        return landedOn.get();
    }

    /**
     * Submits the given details, which are expected to be refused (deliberately wrong or blank
     * values, never real secrets), and returns once the screen's error message is visible.
     * Deliberately not paused with {@link RecordingPause}: these fake values are no secret,
     * and seeing them in a failure trace helps debugging.
     */
    public LoginPage signInExpectingError(String username, String password) {
        usernameField.fill(username);
        passwordField.fill(password);
        signInButton.click();
        errorMessage.waitFor();
        return this;
    }

    /** The error message currently shown on the Sign In screen. */
    public String errorMessage() {
        return errorMessage.textContent().strip();
    }

    /** Whether the Sign In screen is currently displayed. */
    public boolean isShown() {
        return heading.isVisible();
    }
}
