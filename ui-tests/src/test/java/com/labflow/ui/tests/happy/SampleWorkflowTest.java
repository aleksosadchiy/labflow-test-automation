// @layer: e2e
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.happy;

import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.pages.StageState;
import com.labflow.ui.pages.StageView;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sample Details screen shows where a sample stands among the eight workflow stages, and
 * moves it on with Advance until the final stage offers the report instead.
 */
class SampleWorkflowTest extends UiTest {

    @Test
    @DisplayName("A newly registered sample's Details screen shows Accessioned as the current stage")
    void newSampleStartsAtAccessioned() {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        signedIn();

        SampleDetailsPage details = SampleDetailsPage.open(page, config, sample.id());

        assertThat(details.currentStatus())
                .as("the current status shown for a newly registered sample")
                .isEqualTo(WorkflowStage.ACCESSIONED);

        List<WorkflowStage> expectedOrder = List.of(WorkflowStage.values());
        List<WorkflowStage> shownOrder = details.stages().stream().map(StageView::stage).toList();
        assertThat(shownOrder)
                .as("the stages shown on the Details screen, in order")
                .containsExactlyElementsOf(expectedOrder);

        List<StageView> expectedStages = expectedOrder.stream()
                .map(stage -> new StageView(stage,
                        stage == WorkflowStage.ACCESSIONED ? StageState.CURRENT : StageState.REMAINING))
                .collect(Collectors.toList());
        assertThat(details.stages())
                .as("the state of each stage for a newly registered sample: Accessioned current, the rest remaining")
                .containsExactlyElementsOf(expectedStages);
    }

    @Test
    @DisplayName("Advancing a newly registered sample once moves it to Ready for Processing")
    void advancingOnceReachesReadyForProcessing() {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        signedIn();

        SampleDetailsPage details = SampleDetailsPage.open(page, config, sample.id()).advance();

        assertThat(details.currentStatus())
                .as("the current status shown after advancing a newly registered sample once")
                .isEqualTo(WorkflowStage.READY_FOR_PROCESSING);

        List<StageView> expectedStages = List.of(WorkflowStage.values()).stream()
                .map(stage -> new StageView(stage, stateOf(stage)))
                .toList();
        assertThat(details.stages())
                .as("the state of each stage after one advance: Accessioned completed, "
                        + "Ready for Processing current, the remaining six not yet reached")
                .containsExactlyElementsOf(expectedStages);
    }

    private static StageState stateOf(WorkflowStage stage) {
        if (stage == WorkflowStage.ACCESSIONED) {
            return StageState.COMPLETED;
        }
        if (stage == WorkflowStage.READY_FOR_PROCESSING) {
            return StageState.CURRENT;
        }
        return StageState.REMAINING;
    }

    @Test
    @DisplayName("At the final stage, the Details screen offers the report instead of Advance")
    void finalStageOffersReportInsteadOfAdvance() {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        lab.samples().walkTo(sample.id(), WorkflowStage.RESULT_READY);
        signedIn();

        SampleDetailsPage details = SampleDetailsPage.open(page, config, sample.id()).advance();

        assertThat(details.currentStatus())
                .as("the current status shown once the sample reaches its final stage")
                .isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(details.canAdvance())
                .as("whether Advance is still offered at the final stage")
                .isFalse();
        assertThat(details.canViewReport())
                .as("whether View Report is offered at the final stage")
                .isTrue();
    }
}
