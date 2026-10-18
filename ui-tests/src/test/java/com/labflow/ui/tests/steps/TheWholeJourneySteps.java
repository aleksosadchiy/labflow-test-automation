package com.labflow.ui.tests.steps;

import com.labflow.ui.pages.ReportPage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "09. The whole journey" on the screens. They drive the Page Objects:
 * the sample is registered, advanced and read entirely through the screens. The patient comes from the lab's
 * connected interface (the shared step "the lab holds a registered patient"), because the lab has
 * no patient screen.
 *
 * <p>The sample's id and the number of advances are this journey's own state, so they live in this
 * step class: Cucumber creates one instance per scenario.
 */
public class TheWholeJourneySteps {

    private final UiWorld world;
    private String registeredSampleId;
    private int advances;

    public TheWholeJourneySteps(UiWorld world) {
        this.world = world;
    }

    @When("the visitor advances the sample on the Details screen until Advance is no longer offered")
    public void theVisitorAdvancesUntilTheFinalStage() {
        SampleDetailsPage details = world.sampleDetails();
        // Read the id now: it identifies this journey's sample, and Page Objects go stale once
        // the screen moves on, so it must be captured before any Advance.
        registeredSampleId = details.sampleId();
        advances = 0;
        while (details.canAdvance()) {
            details = details.advance();
            advances++;
        }
        world.sampleDetailsIs(details);
    }

    @Then("the sample needed {int} advances to reach the final stage")
    public void theSampleNeededAdvances(int expected) {
        assertThat(advances)
                .as("the number of screen advances a newly registered sample needs to reach the final stage")
                .isEqualTo(expected);
    }

    @When("the visitor opens the report from the Details screen")
    public void theVisitorOpensTheReport() {
        world.reportScreenIs(world.sampleDetails().openReport());
    }

    @Then("the Report screen shows the finished report of the sample registered:")
    public void theReportScreenShowsTheFinishedReport(DataTable table) {
        ReportPage report = world.reportScreen();
        for (Map<String, String> row : table.asMaps()) {
            String shown = row.get("shown on the Report screen");
            switch (shown) {
                case "sample id" -> assertThat(report.sampleId())
                        .as("the Report screen's sample id, compared with the id of the sample registered on screen")
                        .isEqualTo(registeredSampleId);
                case "test name" -> assertThat(report.testName())
                        .as("the Report screen's test name for the finished sample")
                        .isNotBlank();
                case "result value" -> assertThat(report.resultValue())
                        .as("the Report screen's result value for the finished sample")
                        .isNotBlank();
                case "generation date" -> assertThat(report.generatedAt())
                        .as("the Report screen's generation date for the finished sample")
                        .isNotBlank();
                default -> throw new IllegalArgumentException("No such report field: " + shown);
            }
        }
    }
}
