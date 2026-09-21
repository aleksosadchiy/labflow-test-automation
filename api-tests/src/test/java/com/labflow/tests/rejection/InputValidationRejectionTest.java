// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.tests.support.LabTest;
import com.labflow.tests.support.UniqueData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.INVALID_INPUT;
import static com.labflow.client.http.Rejection.NOT_FOUND;

/**
 * The lab refuses requests that are missing something it needs, or that name something it does
 * not know. Every check asserts the kind of refusal, the lab's exact message, that nothing was
 * reported as created (no {@code Location} header) and, for samples, that the patient still has
 * no samples.
 */
class InputValidationRejectionTest extends LabTest {

    @Test
    @DisplayName("A patient cannot be registered without a name, whether it is missing or only blank")
    void patientWithoutNameIsRefused() {
        // null = the name is left out of the request; the others are present but blank.
        for (String name : Arrays.asList(null, "", "   ")) {
            LabResponse<Patient> response = lab.patients().attemptRegister(name);

            assertThat(response)
                    .as("patient name %s", name == null ? "missing" : "\"" + name + "\"")
                    .isRejectedAs(INVALID_INPUT, LabMessages.PATIENT_NAME_REQUIRED)
                    .hasNoLocationHeader();
        }
    }

    @Test
    @DisplayName("A sample cannot be registered without a sample type, and none is created")
    void sampleWithoutTypeIsRefused() {
        Patient patient = lab.patients().register(UniqueData.name("patient"));

        LabResponse<Sample> response = lab.samples().attemptRegister(patient.id(), null);

        assertThat(response)
                .isRejectedAs(INVALID_INPUT, LabMessages.SAMPLE_TYPE_REQUIRED)
                .hasNoLocationHeader();
        assertNoSamplesFor(patient.id());
    }

    @Test
    @DisplayName("A sample cannot be registered without a patient")
    void sampleWithoutPatientIsRefused() {
        LabResponse<Sample> response = lab.samples().attemptRegister(null, SampleType.BLOOD.name());

        assertThat(response)
                .isRejectedAs(INVALID_INPUT, LabMessages.PATIENT_REQUIRED)
                .hasNoLocationHeader();
    }

    @Test
    @DisplayName("An order cannot be placed without a patient")
    void orderWithoutPatientIsRefused() {
        assertThat(lab.orders().attemptPlace(null))
                .isRejectedAs(INVALID_INPUT, LabMessages.PATIENT_REQUIRED)
                .hasNoLocationHeader();
    }

    @Test
    @DisplayName("An order cannot be placed for a patient the lab does not know")
    void orderForUnknownPatientIsRefused() {
        String unknownPatientId = UniqueData.name("unknown-patient");

        assertThat(lab.orders().attemptPlace(unknownPatientId))
                .isRejectedAs(NOT_FOUND, LabMessages.PATIENT_NOT_FOUND)
                .hasNoLocationHeader();
    }

    @Test
    @DisplayName("A sample cannot be registered for a patient the lab does not know, and none is created")
    void sampleForUnknownPatientIsRefused() {
        String unknownPatientId = UniqueData.name("unknown-patient");

        LabResponse<Sample> response = lab.samples().attemptRegister(unknownPatientId, SampleType.BLOOD.name());

        assertThat(response)
                .isRejectedAs(NOT_FOUND, LabMessages.PATIENT_NOT_FOUND)
                .hasNoLocationHeader();
        assertNoSamplesFor(unknownPatientId);
    }

    @Test
    @DisplayName("A sample of a type the lab does not support (PLASMA) is refused, and none is created")
    void unsupportedSampleTypeIsRefused() {
        Patient patient = lab.patients().register(UniqueData.name("patient"));

        LabResponse<Sample> response = lab.samples().attemptRegister(patient.id(), "PLASMA");

        assertThat(response)
                .isRejectedAs(INVALID_INPUT, LabMessages.INVALID_SAMPLE_TYPE)
                .hasNoLocationHeader();
        assertNoSamplesFor(patient.id());
    }

    /** The lab holds no sample for this patient: the refused request created nothing. */
    private void assertNoSamplesFor(String patientId) {
        List<Sample> samples = lab.samples().listFor(patientId);

        Assertions.assertThat(samples)
                .as("samples the lab holds for the patient after the refusal")
                .isEmpty();
    }
}
