// @layer: e2e
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.rejection;

import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.SampleType;
import com.labflow.ui.pages.CreateSamplePage;
import com.labflow.ui.tests.support.UiTest;
import com.labflow.ui.tests.support.UniqueData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Create Sample screen refuses anything it cannot register: a missing field, a patient it
 * does not know, or a sample type it does not support.
 */
class SampleRegistrationRejectionTest extends UiTest {

    /** The two required fields on the Create Sample screen, left blank one at a time. */
    enum MissingField {
        BLANK_PATIENT("A blank patient is refused", true, false, LabMessages.PATIENT_REQUIRED),
        BLANK_SAMPLE_TYPE("A blank sample type is refused", false, true, LabMessages.SAMPLE_TYPE_REQUIRED);

        private final String scenario;
        private final boolean blankPatient;
        private final boolean blankSampleType;
        private final String expectedMessage;

        MissingField(String scenario, boolean blankPatient, boolean blankSampleType, String expectedMessage) {
            this.scenario = scenario;
            this.blankPatient = blankPatient;
            this.blankSampleType = blankSampleType;
            this.expectedMessage = expectedMessage;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MissingField.class)
    @DisplayName("Submitting the Create Sample screen with the patient or the sample type left blank is refused")
    void missingFieldIsRequired(MissingField missingField) {
        Patient patient = newPatient();
        String patientId = missingField.blankPatient ? "" : patient.id();
        String sampleType = missingField.blankSampleType ? "" : SampleType.BLOOD.name();

        CreateSamplePage createSample = signedIn().startNewSample()
                .createSampleExpectingError(patientId, sampleType);

        assertThat(createSample.errorMessage())
                .as("the Create Sample screen's error message for %s", missingField)
                .isEqualTo(missingField.expectedMessage);
    }

    @Test
    @DisplayName("Registering a sample for a patient the lab does not know is refused")
    void unknownPatientIsRefused() {
        String unknownPatientId = UniqueData.name("unknown-patient");

        CreateSamplePage createSample = signedIn().startNewSample()
                .createSampleExpectingError(unknownPatientId, SampleType.BLOOD.name());

        assertThat(createSample.errorMessage())
                .as("the Create Sample screen's error message for a patient the lab does not know")
                .isEqualTo(LabMessages.PATIENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Registering a sample of a type the lab does not support is refused, for a known patient")
    void unsupportedSampleTypeIsRefused() {
        Patient patient = newPatient();

        CreateSamplePage createSample = signedIn().startNewSample()
                .createSampleExpectingError(patient.id(), "PLASMA");

        assertThat(createSample.errorMessage())
                .as("the Create Sample screen's error message for an unsupported sample type")
                .isEqualTo(LabMessages.INVALID_SAMPLE_TYPE);
    }
}
