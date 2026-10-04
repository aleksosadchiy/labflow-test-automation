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

    /**
     * Content the lab cannot read. The label is what a failure shows; the text is never shown,
     * so a failure names which kind of broken body it was and nothing else.
     */
    enum BrokenBody {
        TRUNCATED_JSON("truncated JSON", "{\"patientId\": "),
        PLAIN_TEXT("plain text", "not json at all"),
        EMPTY("empty body", "");

        private final String label;
        private final String text;

        BrokenBody(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    @Test
    @DisplayName("A sample request whose content cannot be read is refused as malformed, and no sample is created")
    void malformedBodyRefused() {
        Patient patient = newPatient();

        for (BrokenBody body : BrokenBody.values()) {
            LabResponse<Sample> response = lab.samples().attemptRegisterRaw(body.text);

            assertThat(response)
                    .as("sample request with a %s", body.label)
                    .isRejectedAs(INVALID_INPUT, LabMessages.MALFORMED_REQUEST_BODY)
                    .hasNoLocationHeader();
        }

        Assertions.assertThat(lab.samples().listFor(patient.id()))
                .as("samples the lab holds for the patient after the refusals")
                .isEmpty();
    }

    @Test
    @DisplayName("A patient request whose content cannot be read is refused as malformed, and no patient is issued")
    void malformedPatientBodyRefused() {
        LabResponse<Patient> response = lab.patients().attemptRegisterRaw("not json at all");

        assertThat(response)
                .isRejectedAs(INVALID_INPUT, LabMessages.MALFORMED_REQUEST_BODY)
                .hasNoLocationHeader();
        // The lab lists no patients, so what a check can observe is that none came back.
        Assertions.assertThat(response.body())
                .as("the patient the lab returned for the refused request")
                .isEmpty();
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
