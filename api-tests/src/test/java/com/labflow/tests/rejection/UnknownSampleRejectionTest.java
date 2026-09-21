// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.client.service.SampleService;
import com.labflow.common.LabMessages;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.support.LabTest;
import com.labflow.tests.support.UniqueData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.BiFunction;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.NOT_FOUND;

/**
 * Whatever is asked about a sample the lab has never heard of, the lab answers "Sample not
 * found." The five things a check can ask are the rows of {@link Request}: the check body is
 * written once, and each row shows up as its own result named after what was asked. A refused
 * request asks to look at or change a sample, never to create one, so there is nothing created
 * to check for.
 */
class UnknownSampleRejectionTest extends LabTest {

    /** What is asked about the unknown sample; the name is the scenario shown in the results. */
    enum Request {
        LOOK_UP("Looking up a sample the lab does not know is refused",
                SampleService::attemptFind),
        // The stage asked for is the valid next one (Ready for Processing), so the only thing
        // wrong with this move is that the sample does not exist.
        MOVE("Moving a sample the lab does not know to its next stage is refused",
                (samples, id) -> samples.attemptMoveTo(id, WorkflowStage.READY_FOR_PROCESSING)),
        PROGRESS("Asking for the progress of a sample the lab does not know is refused",
                SampleService::attemptProgress),
        RESULT("Asking for the result of a sample the lab does not know is refused",
                SampleService::attemptResult),
        REPORT("Asking for the report of a sample the lab does not know is refused",
                SampleService::attemptReport);

        private final String scenario;
        private final BiFunction<SampleService, String, LabResponse<?>> ask;

        Request(String scenario, BiFunction<SampleService, String, LabResponse<?>> ask) {
            this.scenario = scenario;
            this.ask = ask;
        }

        LabResponse<?> askAbout(SampleService samples, String sampleId) {
            return ask.apply(samples, sampleId);
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request.class)
    @DisplayName("Any request about a sample the lab does not know is answered \"Sample not found.\"")
    void unknownSampleIsRefused(Request request) {
        String unknownSampleId = UniqueData.name("unknown-sample");

        LabResponse<?> response = request.askAbout(lab.samples(), unknownSampleId);

        assertThat(response).isRejectedAs(NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND);
    }
}
