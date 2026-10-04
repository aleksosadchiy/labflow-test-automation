// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.happy;

import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.StageEntry;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.support.LabTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FullJourneyTest extends LabTest {

    @Test
    @DisplayName("The whole journey works: patient, order, sample, every stage, result and report, all belonging to the sample registered")
    void wholeJourneyFromPatientToReport() {
        Patient patient = newPatient();
        Order order = lab.orders().place(patient.id());
        Sample registered = lab.samples().register(patient.id(), SampleType.BLOOD, order.id());
        assertThat(registered.orderId()).isEqualTo(order.id());
        assertThat(registered.status()).isEqualTo(WorkflowStage.ACCESSIONED);

        Sample finished = lab.samples().walkTo(registered.id(), WorkflowStage.REPORT_GENERATED);
        TestResult result = lab.samples().result(registered.id());
        Report report = lab.samples().report(registered.id());
        Progress progress = lab.samples().progress(registered.id());
        Order orderAfter = lab.orders().find(order.id());

        assertThat(finished.id()).isEqualTo(registered.id());
        assertThat(finished.status()).isEqualTo(WorkflowStage.REPORT_GENERATED);

        assertThat(result.sampleId()).isEqualTo(registered.id());
        assertThat(result.testName()).isNotBlank();
        assertThat(result.resultValue()).isNotBlank();

        assertThat(report.sampleId()).isEqualTo(registered.id());
        assertThat(report.testName()).isEqualTo(result.testName());
        assertThat(report.resultValue()).isEqualTo(result.resultValue());
        assertThat(report.generatedAt()).isNotNull();

        assertThat(progress.sampleId()).isEqualTo(registered.id());
        assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(progress.stages())
                .extracting(StageEntry::status)
                .containsExactly(WorkflowStage.values());

        assertThat(orderAfter.patientId()).isEqualTo(patient.id());
        assertThat(orderAfter.samples())
                .extracting("id", "sampleType", "status")
                .containsExactly(tuple(registered.id(), SampleType.BLOOD, WorkflowStage.REPORT_GENERATED));
    }
}
