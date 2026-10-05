// @layer: integration
// @spec: 003-test-automation-foundation
package com.labflow.tests.rejection;

import com.labflow.client.http.LabResponse;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleSummary;
import com.labflow.common.model.SampleType;
import com.labflow.testdata.SampleDraft;
import com.labflow.testdata.Samples;
import com.labflow.tests.support.LabTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.CONFLICT;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

/**
 * An order takes at most one sample of each type. The first check proves the lab refuses the
 * second sample of a type (kind, exact message, no {@code Location} header, and the order still
 * holds only the first sample); the second is the positive control that proves the rule is about
 * the <em>type</em>, not about "an order can only have one sample".
 */
class DuplicateSampleRejectionTest extends LabTest {

    /** One row per sample type the lab supports, named in plain language. */
    static Stream<Arguments> everySampleType() {
        return Arrays.stream(SampleType.values())
                .map(type -> argumentSet(
                        "A second " + type.name().toLowerCase() + " sample under the same order is refused, and the order still holds only the first one",
                        type));
    }

    @ParameterizedTest(name = "{argumentSetName}")
    @MethodSource("everySampleType")
    @DisplayName("A second sample of a type the order already holds is refused, and the order still holds only the first one")
    void secondSampleOfSameTypeRefused(SampleType type) {
        Patient patient = newPatient();
        Order order = lab.orders().place(patient.id());
        Sample first = lab.samples().register(patient.id(), type, order.id());

        SampleDraft duplicate = Samples.validFor(patient.id()).ofType(type).inOrder(order.id());

        LabResponse<Sample> second =
                lab.samples().attemptRegister(duplicate.patientId(), duplicate.rawType(), duplicate.orderId());

        assertThat(second)
                .isRejectedAs(CONFLICT, LabMessages.DUPLICATE_SAMPLE_TYPE)
                .hasNoLocationHeader();
        // Nothing was created: the patient's samples and the order's own list show only the first.
        List<Sample> patientSamples = lab.samples().listFor(patient.id());
        Assertions.assertThat(patientSamples)
                .as("samples the lab holds for the patient after the refusal")
                .extracting(Sample::id)
                .containsExactly(first.id());
        Assertions.assertThat(lab.orders().find(order.id()).samples())
                .as("samples the order holds after the refusal")
                .extracting(SampleSummary::id)
                .containsExactly(first.id());
    }

    @Test
    @DisplayName("A tissue sample is accepted under an order that already holds a blood sample, so the rule is about the type")
    void sampleOfOtherTypeAccepted() {
        Patient patient = newPatient();
        Order order = lab.orders().place(patient.id());
        Sample blood = lab.samples().register(patient.id(), SampleType.BLOOD, order.id());

        Sample tissue = lab.samples().register(patient.id(), SampleType.TISSUE, order.id());

        Assertions.assertThat(tissue.id()).isNotBlank().isNotEqualTo(blood.id());
        Assertions.assertThat(tissue.sampleType()).isEqualTo(SampleType.TISSUE);
        Assertions.assertThat(tissue.orderId()).isEqualTo(order.id());
        Assertions.assertThat(lab.samples().listFor(patient.id()))
                .as("samples the lab holds for the patient")
                .extracting(Sample::id)
                .containsExactlyInAnyOrder(blood.id(), tissue.id());
    }
}
