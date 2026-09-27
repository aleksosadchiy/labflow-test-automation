// @layer: e2e
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.rejection;

import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.ui.pages.ReportPage;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Report screen refuses to show a report for a sample that has not yet reached
 * Report Generated (FS 2.7 ¶2). The sample is prepared through the lab's connected interface
 * up to a mid-workflow stage; only the Report screen's refusal goes through the screens.
 */
class ReportRejectionTest extends UiTest {

    @Test
    @DisplayName("Opening the report of a sample that has not reached Report Generated shows 'Report is not ready yet.'")
    void reportNotReadyBeforeFinalStage() {
        Patient patient = newPatient();
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);
        lab.samples().walkTo(sample.id(), WorkflowStage.PROCESSING);
        signedIn();

        ReportPage report = ReportPage.openExpectingNotReady(page, config, sample.id());

        assertThat(report.notReadyMessage())
                .as("the Report screen's message for a sample that has not reached Report Generated")
                .isEqualTo(LabMessages.REPORT_NOT_READY);
    }
}
