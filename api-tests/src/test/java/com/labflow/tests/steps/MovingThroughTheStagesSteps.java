package com.labflow.tests.steps;

import com.labflow.common.model.Progress;
import com.labflow.common.model.Sample;
import com.labflow.common.model.StageEntry;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.world.LabWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "05. Moving through the stages". They move a sample through the
 * seven stages one at a time and expect skipped stages to be refused. The set-up ("the lab holds a sample at ...") and the
 * refusal and "afterwards" steps are shared: see {@link GettingAccessSteps} and {@link PatientsSteps}.
 * The feature file holds stage names as users see them; {@link LabWorld#stageNamed} maps them to
 * the enum.
 */
public class MovingThroughTheStagesSteps {

    /** One accepted move: the answer to the move and the stage found when the sample was looked up. */
    private record Move(WorkflowStage asked, Sample answered, Sample lookedUp) {
    }

    private final LabWorld world;

    private final List<Move> moves = new ArrayList<>();

    public MovingThroughTheStagesSteps(LabWorld world) {
        this.world = world;
    }

    @When("a caller moves the sample one stage at a time through these stages:")
    public void aCallerMovesTheSampleThroughTheStages(DataTable stages) {
        WorkflowStage current = world.sample().status();
        for (Map<String, String> row : stages.asMaps()) {
            WorkflowStage from = LabWorld.stageNamed(row.get("from"));
            WorkflowStage to = LabWorld.stageNamed(row.get("to"));
            assertThat(from).as("the stage the table says the sample is at").isEqualTo(current);
            Sample answered = world.lab().samples().moveTo(world.sample().id(), to);
            Sample lookedUp = world.lab().samples().find(world.sample().id());
            moves.add(new Move(to, answered, lookedUp));
            current = to;
        }
    }

    @Then("every move was accepted for the same sample, which was then at the stage asked for")
    public void everyMoveWasAccepted() {
        assertThat(moves).as("moves made").isNotEmpty();
        for (Move move : moves) {
            assertThat(move.answered().id()).isEqualTo(world.sample().id());
            assertThat(move.answered().status()).isEqualTo(move.asked());
            assertThat(move.lookedUp().status()).isEqualTo(move.asked());
        }
    }

    @Then("the sample is at Report Generated, and its progress lists all eight stages in order, each with the time it was reached")
    public void theSampleIsAtReportGeneratedWithAllStages() {
        String id = world.sample().id();
        assertThat(moves).as("moves made").hasSize(WorkflowStage.values().length - 1);
        assertThat(moves.get(moves.size() - 1).asked()).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(world.lab().samples().find(id).status()).isEqualTo(WorkflowStage.REPORT_GENERATED);

        Progress progress = world.lab().samples().progress(id);
        assertThat(progress.sampleId()).isEqualTo(id);
        assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(progress.stages())
                .extracting(StageEntry::status)
                .containsExactly(WorkflowStage.values());
        assertThat(progress.stages())
                .extracting(StageEntry::reachedAt)
                .doesNotContainNull();
    }

    @When("^a caller asks the lab to move the sample to (.+)$")
    public void aCallerAsksTheLabToMoveTheSampleTo(String stageName) {
        Sample sample = world.sample();
        WorkflowStage stayedAt = sample.status();
        world.answerWas(world.lab().samples().attemptMoveTo(sample.id(), LabWorld.stageNamed(stageName)));
        world.nothingChangedCanBeShownBy(() -> assertThat(world.lab().samples().find(sample.id()).status())
                .as("the sample's stage after the refused move")
                .isEqualTo(stayedAt));
    }
}
