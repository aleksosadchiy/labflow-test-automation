package com.labflow.ui.tests.steps;

import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "07. Unknown samples" on the screens. They drive the Details
 * Page Object with a plainly fake, uniquely generated sample id
 * the lab has never issued.
 */
public class UnknownSamplesSteps {

    private final UiWorld world;

    public UnknownSamplesSteps(UiWorld world) {
        this.world = world;
    }

    @When("the visitor opens the Details screen of a sample the lab does not know")
    public void theVisitorOpensTheDetailsOfAnUnknownSample() {
        String unknownSampleId = UniqueData.name("SMP-unknown");
        world.sampleDetailsIs(SampleDetailsPage.openExpectingNotFound(world.page(), world.config(), unknownSampleId));
    }

    @Then("the Details screen says {string}")
    public void theDetailsScreenSays(String message) {
        assertThat(world.sampleDetails().notFoundMessage())
                .as("the Details screen's message for a sample id the lab does not know")
                .isEqualTo(message);
    }
}
