// @layer: e2e
// @spec: 004-ui-automation-page-objects
package com.labflow.ui.tests.rejection;

import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.LoginPage;
import com.labflow.ui.pages.SampleListPage;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sign In screen refuses anything but the lab's valid details, and any other screen sends a
 * signed-out visitor to Sign In instead of showing itself.
 *
 * <p>The wrong password below is a plainly fake, uniquely generated text; the real password is
 * never written here.
 */
class SignInRejectionTest extends UiTest {

    @Test
    @DisplayName("Signing in with a wrong password is refused, and the Sign In screen stays")
    void wrongPasswordIsRefused() {
        LoginPage loginPage = LoginPage.open(page, config)
                .signInExpectingError(credentials.username(), UniqueData.name("wrong-password"));

        assertThat(loginPage.errorMessage())
                .as("the Sign In screen's error message for a wrong password")
                .isEqualTo("Invalid username or password.");
        assertThat(loginPage.isShown())
                .as("whether the Sign In screen is still shown after the refusal")
                .isTrue();
    }

    /** The two ways of leaving a required field blank on the Sign In screen. */
    enum BlankField {
        BLANK_USERNAME("A blank username is refused", "", UniqueData.name("password")),
        BLANK_PASSWORD("A blank password is refused", UniqueData.name("username"), "");

        private final String scenario;
        private final String username;
        private final String password;

        BlankField(String scenario, String username, String password) {
            this.scenario = scenario;
            this.username = username;
            this.password = password;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BlankField.class)
    @DisplayName("Submitting the Sign In screen with the username or the password left blank is refused")
    void blankCredentialsAreRefused(BlankField blankField) {
        LoginPage loginPage = LoginPage.open(page, config)
                .signInExpectingError(blankField.username, blankField.password);

        assertThat(loginPage.errorMessage())
                .as("the Sign In screen's error message for %s", blankField)
                .isEqualTo("Username and password are required.");
    }

    @Test
    @DisplayName("Opening the Sample List screen without signing in shows the Sign In screen instead")
    void screensRequireSignIn() {
        LoginPage loginPage = SampleListPage.openExpectingSignIn(page, config);

        assertThat(loginPage.isShown())
                .as("whether the Sign In screen is shown to a signed-out visitor")
                .isTrue();
    }
}
