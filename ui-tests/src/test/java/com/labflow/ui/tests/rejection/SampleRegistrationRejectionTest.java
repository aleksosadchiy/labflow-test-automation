// @layer: e2e
// @spec: 004-ui-automation-page-objects
package com.labflow.ui.tests.rejection;

import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.testdata.SampleDraft;
import com.labflow.testdata.Samples;
import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.CreateSamplePage;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
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
        SampleDraft sample = Samples.validFor(patient.id());
        String patientId = missingField.blankPatient ? "" : sample.patientId();
        String sampleType = missingField.blankSampleType ? "" : sample.rawType();

        CreateSamplePage createSample = signedIn().startNewSample()
                .createSampleExpectingError(patientId, sampleType);

        assertThat(createSample.errorMessage())
                .as("the Create Sample screen's error message for %s", missingField)
                .isEqualTo(missingField.expectedMessage);
    }

    /**
     * A sample the screen cannot register even though both fields are filled in. A row holds a
     * description of the input (whether the patient is one the lab has, and the raw sample type),
     * never a created patient: each run of a row registers its own.
     */
    enum UnacceptableSample {
        UNKNOWN_PATIENT("A sample for a patient the lab does not know is refused", false, null,
                LabMessages.PATIENT_NOT_FOUND),
        UNSUPPORTED_TYPE("A sample of a type the lab does not support (PLASMA) is refused, for a known patient", true, "PLASMA",
                LabMessages.INVALID_SAMPLE_TYPE);

        private final String scenario;
        private final boolean knownPatient;
        private final String rawTypeOrNullForValid;
        private final String expectedMessage;

        UnacceptableSample(String scenario, boolean knownPatient, String rawTypeOrNullForValid, String expectedMessage) {
            this.scenario = scenario;
            this.knownPatient = knownPatient;
            this.rawTypeOrNullForValid = rawTypeOrNullForValid;
            this.expectedMessage = expectedMessage;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(UnacceptableSample.class)
    @DisplayName("Registering a sample for an unknown patient or of an unsupported type is refused")
    void unacceptableSampleIsRefused(UnacceptableSample unacceptable) {
        String patientId = unacceptable.knownPatient ? newPatient().id() : UniqueData.name("unknown-patient");
        SampleDraft sample = Samples.validFor(patientId);
        if (unacceptable.rawTypeOrNullForValid != null) {
            sample = sample.withRawType(unacceptable.rawTypeOrNullForValid);
        }

        CreateSamplePage createSample = signedIn().startNewSample()
                .createSampleExpectingError(sample.patientId(), sample.rawType());

        assertThat(createSample.errorMessage())
                .as("the Create Sample screen's error message for %s", unacceptable)
                .isEqualTo(unacceptable.expectedMessage);
    }
}
