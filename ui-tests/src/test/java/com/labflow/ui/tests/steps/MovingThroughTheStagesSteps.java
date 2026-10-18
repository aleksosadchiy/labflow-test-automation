package com.labflow.ui.tests.steps;

import com.labflow.common.model.WorkflowStage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.pages.StageState;
import com.labflow.ui.pages.StageView;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "05. Moving through the stages" on the screens. They drive the
 * Page Objects of the Details screen.
 *
 * <p>The feature file holds stage names as users see them and state labels ("completed",
 * "current", "remaining"); the steps map them to the framework's types. A sample at a later
 * stage is prepared through the lab's connected interface, as the old check did.
 */
public class MovingThroughTheStagesSteps {

    private final UiWorld world;

    public MovingThroughTheStagesSteps(UiWorld world) {
        this.world = world;
    }

    @Given("^the lab holds a sample at (.+)$")
    public void theLabHoldsASampleAtStage(String stageName) {
        world.sampleHeldAt(stage(stageName));
    }

    @When("the visitor opens the Details screen of the sample")
    public void theVisitorOpensTheDetailsScreen() {
        world.sampleDetailsIs(SampleDetailsPage.open(world.page(), world.config(), world.sampleId()));
    }

    @When("the visitor advances the sample on the Details screen")
    public void theVisitorAdvancesTheSample() {
        world.sampleDetailsIs(world.sampleDetails().advance());
    }

    @Then("^the Details screen shows (.+) as the current stage$")
    public void theDetailsScreenShowsTheCurrentStage(String stageName) {
        assertThat(world.sampleDetails().currentStatus())
                .as("the current status shown on the Details screen")
                .isEqualTo(stage(stageName));
    }

    @Then("the Details screen lists the stages in this order, each in this state:")
    public void theDetailsScreenListsTheStages(DataTable table) {
        List<StageView> expected = table.asMaps().stream()
                .map(row -> new StageView(stage(row.get("stage")), state(row)))
                .toList();

        List<StageView> shown = world.sampleDetails().stages();

        assertThat(shown.stream().map(StageView::stage).toList())
                .as("the stages shown on the Details screen, in order")
                .containsExactlyElementsOf(List.of(WorkflowStage.values()));
        assertThat(shown)
                .as("the state of each stage shown on the Details screen")
                .containsExactlyElementsOf(expected);
    }

    @Then("the Details screen does not offer Advance")
    public void theDetailsScreenDoesNotOfferAdvance() {
        assertThat(world.sampleDetails().canAdvance())
                .as("whether Advance is still offered at the final stage")
                .isFalse();
    }

    @Then("the Details screen offers View Report")
    public void theDetailsScreenOffersViewReport() {
        assertThat(world.sampleDetails().canViewReport())
                .as("whether View Report is offered at the final stage")
                .isTrue();
    }

    private static WorkflowStage stage(String displayName) {
        return Arrays.stream(WorkflowStage.values())
                .filter(stage -> stage.displayName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such stage: " + displayName));
    }

    private static StageState state(Map<String, String> row) {
        return switch (row.get("state")) {
            case "completed" -> StageState.COMPLETED;
            case "current" -> StageState.CURRENT;
            case "remaining" -> StageState.REMAINING;
            default -> throw new IllegalArgumentException("No such state: " + row.get("state"));
        };
    }
}
