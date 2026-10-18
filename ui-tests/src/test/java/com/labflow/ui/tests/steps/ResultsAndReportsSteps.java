package com.labflow.ui.tests.steps;

import com.labflow.ui.pages.ReportPage;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "06. Results and reports" on the screens. They drive the report
 * Page Object. The sample is prepared by the shared step
 * "the lab holds a sample at ...", through the lab's connected interface.
 */
public class ResultsAndReportsSteps {

    private final UiWorld world;

    public ResultsAndReportsSteps(UiWorld world) {
        this.world = world;
    }

    @When("the visitor opens the Report screen of the sample")
    public void theVisitorOpensTheReportScreen() {
        world.reportScreenIs(ReportPage.openExpectingNotReady(world.page(), world.config(), world.sampleId()));
    }

    @Then("the Report screen says {string}")
    public void theReportScreenSays(String message) {
        assertThat(world.reportScreen().notReadyMessage())
                .as("the Report screen's message for a sample that has not reached Report Generated")
                .isEqualTo(message);
    }
}
