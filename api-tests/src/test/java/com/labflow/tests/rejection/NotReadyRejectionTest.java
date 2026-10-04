// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.client.service.SampleService;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.support.LabTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.BiFunction;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.CONFLICT;
import static com.labflow.common.model.WorkflowStage.ACCESSIONED;
import static com.labflow.common.model.WorkflowStage.QUALITY_CHECK;
import static com.labflow.common.model.WorkflowStage.RESULT_READY;

/**
 * A sample's result exists only from Result Ready on, and its report only at Report Generated.
 * Asking earlier is a conflict with the lab's "not ready yet" message. Each row of
 * {@link TooEarly} is one such request, including the two boundary cases (the result one stage
 * before Result Ready, the report at Result Ready). The matching successful answers are already
 * proven by the happy-path checks, so they are not repeated here.
 */
class NotReadyRejectionTest extends LabTest {

    /** One request made too early: the stage the sample is at, what is asked, and what the lab must say. */
    enum TooEarly {
        RESULT_AT_ACCESSIONED("The result of a sample at Accessioned is not ready yet",
                ACCESSIONED, SampleService::attemptResult, LabMessages.RESULT_NOT_READY),
        RESULT_AT_QUALITY_CHECK("The result of a sample at Quality Check, one stage before Result Ready, is not ready yet",
                QUALITY_CHECK, SampleService::attemptResult, LabMessages.RESULT_NOT_READY),
        REPORT_AT_ACCESSIONED("The report of a sample at Accessioned is not ready yet",
                ACCESSIONED, SampleService::attemptReport, LabMessages.REPORT_NOT_READY),
        REPORT_AT_RESULT_READY("The report of a sample at Result Ready, one stage before Report Generated, is not ready yet",
                RESULT_READY, SampleService::attemptReport, LabMessages.REPORT_NOT_READY);

        private final String scenario;
        private final WorkflowStage stage;
        private final BiFunction<SampleService, String, LabResponse<?>> ask;
        private final String message;

        TooEarly(String scenario, WorkflowStage stage,
                 BiFunction<SampleService, String, LabResponse<?>> ask, String message) {
            this.scenario = scenario;
            this.stage = stage;
            this.ask = ask;
            this.message = message;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TooEarly.class)
    @DisplayName("A result or report asked for before its stage is refused with the matching \"not ready yet\" message")
    void requestedTooEarlyIsRefused(TooEarly request) {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        if (request.stage != ACCESSIONED) {
            lab.samples().walkTo(sample.id(), request.stage);
        }

        LabResponse<?> response = request.ask.apply(lab.samples(), sample.id());

        assertThat(response).isRejectedAs(CONFLICT, request.message);
    }
}
