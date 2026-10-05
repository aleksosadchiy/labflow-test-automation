// @layer: integration
// @spec: 003-test-automation-foundation
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.client.http.Rejection;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.testdata.OrderDraft;
import com.labflow.testdata.Orders;
import com.labflow.testdata.PatientDraft;
import com.labflow.testdata.Patients;
import com.labflow.testdata.SampleDraft;
import com.labflow.testdata.Samples;
import com.labflow.testdata.UniqueData;
import com.labflow.tests.support.LabTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.function.UnaryOperator;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.INVALID_INPUT;
import static com.labflow.client.http.Rejection.NOT_FOUND;

/**
 * The lab refuses requests that are missing something it needs, or that name something it does
 * not know. Each table below is one kind of request with the inputs that break it; every row
 * asserts the kind of refusal, the lab's exact message, that nothing was reported as created
 * (no {@code Location} header) and, for samples, that the patient still has no samples. A row
 * holds only a description of the broken input (a variation of the shared recipe), never a
 * created record: each run of a row creates its own patient from the recipes.
 */
class InputValidationRejectionTest extends LabTest {

    /** The ways a patient's name can be unusable. */
    enum BadPatientName {
        MISSING("A patient without a name (left out of the request) is refused", PatientDraft::withoutName),
        EMPTY("A patient with an empty name is refused", draft -> draft.withName("")),
        BLANK("A patient whose name is only blank spaces is refused", PatientDraft::blankName);

        private final String scenario;
        private final UnaryOperator<PatientDraft> variation;

        BadPatientName(String scenario, UnaryOperator<PatientDraft> variation) {
            this.scenario = scenario;
            this.variation = variation;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BadPatientName.class)
    @DisplayName("A patient cannot be registered without a usable name, and none is issued")
    void patientWithoutUsableNameIsRefused(BadPatientName badName) {
        PatientDraft draft = badName.variation.apply(Patients.valid());

        LabResponse<Patient> response = lab.patients().attemptRegister(draft.name());

        assertThat(response)
                .isRejectedAs(INVALID_INPUT, LabMessages.PATIENT_NAME_REQUIRED)
                .hasNoLocationHeader();
    }

    /**
     * A sample request that breaks one rule. {@code knownPatient} says whether the request names
     * a patient the lab has (a new one is registered for the row) or one it has never seen.
     */
    enum BadSampleInput {
        WITHOUT_PATIENT("A sample without a patient is refused", false, SampleDraft::withoutPatient,
                INVALID_INPUT, LabMessages.PATIENT_REQUIRED),
        UNKNOWN_PATIENT("A sample for a patient the lab does not know is refused", false, draft -> draft,
                NOT_FOUND, LabMessages.PATIENT_NOT_FOUND),
        WITHOUT_TYPE("A sample without a sample type is refused", true, SampleDraft::withoutType,
                INVALID_INPUT, LabMessages.SAMPLE_TYPE_REQUIRED),
        UNSUPPORTED_TYPE("A sample of a type the lab does not support (PLASMA) is refused", true,
                draft -> draft.withRawType("PLASMA"), INVALID_INPUT, LabMessages.INVALID_SAMPLE_TYPE);

        private final String scenario;
        private final boolean knownPatient;
        private final UnaryOperator<SampleDraft> variation;
        private final Rejection expectedRejection;
        private final String expectedMessage;

        BadSampleInput(String scenario, boolean knownPatient, UnaryOperator<SampleDraft> variation,
                       Rejection expectedRejection, String expectedMessage) {
            this.scenario = scenario;
            this.knownPatient = knownPatient;
            this.variation = variation;
            this.expectedRejection = expectedRejection;
            this.expectedMessage = expectedMessage;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BadSampleInput.class)
    @DisplayName("A sample request that is missing something or names something unknown is refused, and no sample is created")
    void badSampleInputIsRefused(BadSampleInput input) {
        String patientId = input.knownPatient ? newPatient().id() : UniqueData.name("unknown-patient");
        SampleDraft sample = input.variation.apply(Samples.validFor(patientId));

        LabResponse<Sample> response = lab.samples().attemptRegister(sample.patientId(), sample.rawType());

        assertThat(response)
                .isRejectedAs(input.expectedRejection, input.expectedMessage)
                .hasNoLocationHeader();
        assertNoSamplesFor(patientId);
    }

    /** An order request that breaks one rule; see {@link BadSampleInput}. */
    enum BadOrderInput {
        WITHOUT_PATIENT("An order without a patient is refused", OrderDraft::withoutPatient,
                INVALID_INPUT, LabMessages.PATIENT_REQUIRED),
        UNKNOWN_PATIENT("An order for a patient the lab does not know is refused", draft -> draft,
                NOT_FOUND, LabMessages.PATIENT_NOT_FOUND);

        private final String scenario;
        private final UnaryOperator<OrderDraft> variation;
        private final Rejection expectedRejection;
        private final String expectedMessage;

        BadOrderInput(String scenario, UnaryOperator<OrderDraft> variation,
                      Rejection expectedRejection, String expectedMessage) {
            this.scenario = scenario;
            this.variation = variation;
            this.expectedRejection = expectedRejection;
            this.expectedMessage = expectedMessage;
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BadOrderInput.class)
    @DisplayName("An order request that is missing the patient or names an unknown one is refused")
    void badOrderInputIsRefused(BadOrderInput input) {
        OrderDraft order = input.variation.apply(Orders.validFor(UniqueData.name("unknown-patient")));

        assertThat(lab.orders().attemptPlace(order.patientId()))
                .isRejectedAs(input.expectedRejection, input.expectedMessage)
                .hasNoLocationHeader();
    }

    /** The lab holds no sample for this patient: the refused request created nothing. */
    private void assertNoSamplesFor(String patientId) {
        List<Sample> samples = lab.samples().listFor(patientId);

        Assertions.assertThat(samples)
                .as("samples the lab holds for the patient after the refusal")
                .isEmpty();
    }
}
