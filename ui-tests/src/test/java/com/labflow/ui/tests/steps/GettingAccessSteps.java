package com.labflow.ui.tests.steps;

import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.LoginPage;
import com.labflow.ui.pages.SampleListPage;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "01. Getting access". They drive the sign-in Page Objects.
 *
 * <p>The feature file holds labels ("a wrong password", "left blank"), never a value: the step
 * resolves the label. The wrong values are plainly fake, uniquely generated texts; the real
 * password is only ever read from the settings by the Page Object's sign-in.
 */
public class GettingAccessSteps {

    private final UiWorld world;

    public GettingAccessSteps(UiWorld world) {
        this.world = world;
    }

    @Given("the visitor opens the Sign In screen")
    public void theVisitorOpensTheSignInScreen() {
        world.signInScreenIs(LoginPage.open(world.page(), world.config()));
    }

    @Given("the visitor has not signed in")
    public void theVisitorHasNotSignedIn() {
        // Nothing to do: every scenario starts in a fresh browser context, with no session.
    }

    @When("the visitor signs in with the lab's valid details")
    public void theVisitorSignsInWithTheLabsValidDetails() {
        world.sampleListIs(world.signInScreen().signIn(world.credentials()));
    }

    @When("the visitor signs in with the right username and a wrong password")
    public void theVisitorSignsInWithAWrongPassword() {
        world.signInScreenIs(world.signInScreen()
                .signInExpectingError(world.credentials().username(), UniqueData.name("wrong-password")));
    }

    @When("^the visitor submits the Sign In screen with the (username|password) left blank$")
    public void theVisitorLeavesAFieldBlank(String field) {
        String username = "username".equals(field) ? "" : UniqueData.name("username");
        String password = "password".equals(field) ? "" : UniqueData.name("password");
        world.signInScreenIs(world.signInScreen().signInExpectingError(username, password));
    }

    @When("the visitor opens the Sample List screen")
    public void theVisitorOpensTheSampleListScreen() {
        world.signInScreenIs(SampleListPage.openExpectingSignIn(world.page(), world.config()));
    }

    @Then("the Sample List screen is shown")
    public void theSampleListScreenIsShown() {
        assertThat(world.sampleList().isShown())
                .as("whether the Sample List screen is shown after signing in")
                .isTrue();
    }

    @Then("the Sign In screen says {string}")
    public void theSignInScreenSays(String message) {
        assertThat(world.signInScreen().errorMessage())
                .as("the Sign In screen's error message")
                .isEqualTo(message);
    }

    @Then("the Sign In screen is still shown")
    public void theSignInScreenIsStillShown() {
        assertThat(world.signInScreen().isShown())
                .as("whether the Sign In screen is still shown after the refusal")
                .isTrue();
    }

    @Then("the Sign In screen is shown instead")
    public void theSignInScreenIsShownInstead() {
        assertThat(world.signInScreen().isShown())
                .as("whether the Sign In screen is shown to a signed-out visitor")
                .isTrue();
    }
}
