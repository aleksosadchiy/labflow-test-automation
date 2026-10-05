// @layer: integration
// @spec: 003-test-automation-foundation
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.support.LabTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.CONFLICT;
import static com.labflow.common.model.WorkflowStage.ACCESSIONED;
import static com.labflow.common.model.WorkflowStage.PROCESSING;
import static com.labflow.common.model.WorkflowStage.REPORT_GENERATED;

/**
 * The lab moves a sample forward one stage at a time and refuses every other move with the
 * message "Invalid status transition: cannot move from X to Y." Each row of {@link Move} is one
 * kind of wrong move: the sample is walked to the row's starting stage, asked to go to the
 * row's requested stage, and the check proves the kind (conflict), the exact message and that
 * the sample is still at the stage it started from.
 */
class InvalidTransitionRejectionTest extends LabTest {

    /** One wrong move: where the sample is, and the stage it is (wrongly) asked to go to. */
    enum Move {
        // The project brief lists Accessioned -> Processing as valid, but the lab (spec 002)
        // accepts only the very NEXT stage (Ready for Processing), so skipping ahead is a refusal.
        SKIP_AHEAD("A sample at Accessioned cannot skip ahead to Processing, and stays at Accessioned",
                ACCESSIONED, PROCESSING),
        GO_BACKWARDS("A sample at Processing cannot go back to Accessioned, and stays at Processing",
                PROCESSING, ACCESSIONED),
        REPEAT_CURRENT("A sample at Processing cannot be moved to Processing again, and stays at Processing",
                PROCESSING, PROCESSING),
        // Nothing follows Report Generated; asking to start over is the "move on" that is refused.
        MOVE_ON_FROM_FINAL("A sample at Report Generated cannot be moved on to any stage, and stays at Report Generated",
                REPORT_GENERATED, ACCESSIONED);

        private final String scenario;
        private final WorkflowStage from;
        private final WorkflowStage to;

        Move(String scenario, WorkflowStage from, WorkflowStage to) {
            this.scenario = scenario;
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Move.class)
    @DisplayName("A move to any stage other than the next one is refused, and the sample's stage is unchanged")
    void wrongMoveIsRefused(Move move) {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        if (move.from != ACCESSIONED) {
            lab.samples().walkTo(sample.id(), move.from);
        }

        LabResponse<Sample> response = lab.samples().attemptMoveTo(sample.id(), move.to);

        assertThat(response)
                .isRejectedAs(CONFLICT, LabMessages.invalidTransition(move.from, move.to));
        Assertions.assertThat(lab.samples().find(sample.id()).status())
                .as("the sample's stage after the refused move")
                .isEqualTo(move.from);
    }
}
