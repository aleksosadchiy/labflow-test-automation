// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.happy;

import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.Patients;
import com.labflow.tests.support.LabTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PatientOrderSampleTest extends LabTest {

    @Test
    @DisplayName("A registered patient can be looked up with its lab-assigned id and name")
    void registeredPatientCanBeLookedUp() {
        String name = Patients.valid().name();

        Patient registered = lab.patients().register(name);
        Patient found = lab.patients().find(registered.id());

        assertThat(found.id()).isNotBlank().isEqualTo(registered.id());
        assertThat(found.name()).isEqualTo(name);
    }

    @Test
    @DisplayName("A placed order can be looked up for its patient, with a creation time and no samples yet")
    void placedOrderCanBeLookedUp() {
        Patient patient = newPatient();

        Order placed = lab.orders().place(patient.id());
        Order found = lab.orders().find(placed.id());

        assertThat(found.id()).isNotBlank().isEqualTo(placed.id());
        assertThat(found.patientId()).isEqualTo(patient.id());
        assertThat(found.createdAt()).isNotNull();
        assertThat(found.samples()).isEmpty();
    }

    @Test
    @DisplayName("A sample registered under an order can be looked up and is listed by its order and its patient")
    void registeredSampleCanBeLookedUp() {
        Patient patient = newPatient();
        Order order = lab.orders().place(patient.id());

        Sample registered = lab.samples().register(patient.id(), SampleType.BLOOD, order.id());
        Sample found = lab.samples().find(registered.id());

        assertThat(found.id()).isNotBlank().isEqualTo(registered.id());
        assertThat(found.patientId()).isEqualTo(patient.id());
        assertThat(found.sampleType()).isEqualTo(SampleType.BLOOD);
        assertThat(found.orderId()).isEqualTo(order.id());

        Order orderAfter = lab.orders().find(order.id());
        assertThat(orderAfter.samples())
                .extracting("id", "sampleType")
                .containsExactly(tuple(registered.id(), SampleType.BLOOD));

        assertThat(lab.samples().listFor(patient.id()))
                .extracting(Sample::id)
                .containsExactly(registered.id());
    }

    @Test
    @DisplayName("A newly registered sample starts at the Accessioned stage")
    void newSampleIsAccessioned() {
        Patient patient = newPatient();

        Sample registered = lab.samples().register(patient.id(), SampleType.TISSUE);
        Sample found = lab.samples().find(registered.id());

        assertThat(registered.status()).isEqualTo(WorkflowStage.ACCESSIONED);
        assertThat(found.status()).isEqualTo(WorkflowStage.ACCESSIONED);
    }
}
