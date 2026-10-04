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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.INVALID_INPUT;
import static com.labflow.client.http.Rejection.METHOD_NOT_ALLOWED;

/**
 * The lab refuses a request whose content it cannot read ("Malformed request body.") and an
 * action it does not offer on a sample ("Method not allowed.", with the list of actions it does
 * allow). Each check proves the kind of refusal, the lab's exact message, the supporting detail
 * and that nothing was created or removed.
 */
class BadRequestRejectionTest extends LabTest {

    /** Where a request is sent: the two endpoints that read a request body. */
    enum Target { SAMPLE, PATIENT }

    /**
     * One kind of request the lab cannot read. The scenario is what a run shows for the row; the
     * body text is never shown, so a failure names the kind of broken request and nothing else.
     */
    enum MalformedRequest {
        SAMPLE_TRUNCATED_JSON("A sample request with truncated JSON is refused as malformed, and no sample is created",
                Target.SAMPLE, "{\"patientId\": "),
        SAMPLE_PLAIN_TEXT("A sample request with plain text instead of JSON is refused as malformed, and no sample is created",
                Target.SAMPLE, "not json at all"),
        SAMPLE_EMPTY("A sample request with an empty body is refused as malformed, and no sample is created",
                Target.SAMPLE, ""),
        PATIENT_PLAIN_TEXT("A patient request with plain text instead of JSON is refused as malformed, and no patient is issued",
                Target.PATIENT, "not json at all");

        private final String scenario;
        private final Target target;
        private final String text;

        MalformedRequest(String scenario, Target target, String text) {
            this.scenario = scenario;
            this.target = target;
            this.text = text;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MalformedRequest.class)
    @DisplayName("A request whose content cannot be read is refused as malformed, and nothing is created")
    void malformedBodyRefused(MalformedRequest request) {
        if (request.target == Target.SAMPLE) {
            Patient patient = newPatient();

            LabResponse<Sample> response = lab.samples().attemptRegisterRaw(request.text);

            assertThat(response)
                    .isRejectedAs(INVALID_INPUT, LabMessages.MALFORMED_REQUEST_BODY)
                    .hasNoLocationHeader();
            Assertions.assertThat(lab.samples().listFor(patient.id()))
                    .as("samples the lab holds for the patient after the refusal")
                    .isEmpty();
        } else {
            LabResponse<Patient> response = lab.patients().attemptRegisterRaw(request.text);

            assertThat(response)
                    .isRejectedAs(INVALID_INPUT, LabMessages.MALFORMED_REQUEST_BODY)
                    .hasNoLocationHeader();
            // The lab lists no patients, so what a check can observe is that none came back.
            Assertions.assertThat(response.body())
                    .as("the patient the lab returned for the refused request")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Deleting a sample is not offered: it is refused as not allowed, lists GET as allowed, and the sample stays")
    void deleteSampleIsMethodNotAllowed() {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);

        LabResponse<Void> response = lab.samples().attemptDelete(sample.id());

        // Only that GET is allowed is the contract; the exact list of allowed actions is not.
        assertThat(response)
                .isRejectedAs(METHOD_NOT_ALLOWED, LabMessages.METHOD_NOT_ALLOWED)
                .hasHeaderContaining("Allow", "GET");
        Sample afterwards = lab.samples().find(sample.id());
        Assertions.assertThat(afterwards.id())
                .as("the sample the lab still holds after the refused delete")
                .isEqualTo(sample.id());
        Assertions.assertThat(afterwards.status())
                .as("the sample's stage after the refused delete")
                .isEqualTo(WorkflowStage.ACCESSIONED);
    }
}
