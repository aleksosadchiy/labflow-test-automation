// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.happy;

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

class SampleProgressionTest extends LabTest {

    @Test
    @DisplayName("A sample moves through all seven remaining stages one at a time, and its progress lists all eight in order")
    void movesThroughAllSevenStagesOneAtATime() {
        Patient patient = newPatient();
        Sample registered = lab.samples().register(patient.id(), SampleType.BLOOD);
        assertThat(registered.status()).isEqualTo(WorkflowStage.ACCESSIONED);

        WorkflowStage stage = registered.status();
        int moves = 0;
        while (stage.next().isPresent()) {
            WorkflowStage nextStage = stage.next().get();

            Sample moved = lab.samples().moveTo(registered.id(), nextStage);

            assertThat(moved.id()).isEqualTo(registered.id());
            assertThat(moved.status()).isEqualTo(nextStage);
            assertThat(lab.samples().find(registered.id()).status()).isEqualTo(nextStage);
            stage = nextStage;
            moves++;
        }

        assertThat(moves).isEqualTo(7);
        assertThat(stage).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(lab.samples().find(registered.id()).status()).isEqualTo(WorkflowStage.REPORT_GENERATED);

        Progress progress = lab.samples().progress(registered.id());
        assertThat(progress.sampleId()).isEqualTo(registered.id());
        assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(progress.stages())
                .extracting(StageEntry::status)
                .containsExactly(WorkflowStage.values());
        assertThat(progress.stages())
                .extracting(StageEntry::reachedAt)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("A sample's test result becomes available at Result Ready, with its sample id, test name and result value")
    void resultAvailableAtResultReady() {
        Patient patient = newPatient();
        Sample registered = lab.samples().register(patient.id(), SampleType.SWAB);

        Sample atResultReady = lab.samples().walkTo(registered.id(), WorkflowStage.RESULT_READY);
        TestResult result = lab.samples().result(registered.id());

        assertThat(atResultReady.status()).isEqualTo(WorkflowStage.RESULT_READY);
        assertThat(result.sampleId()).isEqualTo(registered.id());
        assertThat(result.testName()).isNotBlank();
        assertThat(result.resultValue()).isNotBlank();
    }

    @Test
    @DisplayName("A sample's report becomes available at Report Generated, with sample id, test name, result value and generation time, and the earlier result still matches it")
    void reportAvailableAtReportGenerated() {
        Patient patient = newPatient();
        Sample registered = lab.samples().register(patient.id(), SampleType.TISSUE);

        Sample atReportGenerated = lab.samples().walkTo(registered.id(), WorkflowStage.REPORT_GENERATED);
        Report report = lab.samples().report(registered.id());
        TestResult result = lab.samples().result(registered.id());

        assertThat(atReportGenerated.status()).isEqualTo(WorkflowStage.REPORT_GENERATED);
        assertThat(report.sampleId()).isEqualTo(registered.id());
        assertThat(report.testName()).isNotBlank();
        assertThat(report.resultValue()).isNotBlank();
        assertThat(report.generatedAt()).isNotNull();

        assertThat(result.sampleId()).isEqualTo(registered.id());
        assertThat(result.testName()).isEqualTo(report.testName());
        assertThat(result.resultValue()).isEqualTo(report.resultValue());
    }
}
