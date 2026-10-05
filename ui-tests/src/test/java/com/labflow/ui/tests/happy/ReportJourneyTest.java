// @layer: e2e
// @spec: 004-ui-automation-page-objects
package com.labflow.ui.tests.happy;

import com.labflow.common.junit.Smoke;
import com.labflow.common.model.Patient;
import com.labflow.common.model.SampleType;
import com.labflow.ui.pages.ReportPage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one full end-to-end journey through the screens (FS 2.7 ¶1): a patient comes from the
 * lab's connected interface, because the lab has no patient screen, but the sample itself is
 * registered, advanced and read entirely through the screens.
 */
class ReportJourneyTest extends UiTest {

    @Test
    @Smoke
    @DisplayName("Following a sample through every screen from registration to Report Generated shows its finished report")
    void fullJourneyThroughScreensEndsInReport() {
        Patient patient = newPatient();

        SampleDetailsPage details = signedIn()
                .startNewSample()
                .createSample(patient.id(), SampleType.BLOOD.name());
        // Read the id now: it identifies this journey's sample, and Page Objects go stale once
        // the screen moves on, so it must be captured before any Advance.
        String sampleId = details.sampleId();

        int advances = 0;
        while (details.canAdvance()) {
            details = details.advance();
            advances++;
        }

        assertThat(advances)
                .as("the number of screen advances a newly registered sample needs to reach the final stage")
                .isEqualTo(7);

        ReportPage report = details.openReport();

        assertThat(report.sampleId())
                .as("the Report screen's sample id, compared with the id of the sample registered on screen")
                .isEqualTo(sampleId);
        assertThat(report.testName())
                .as("the Report screen's test name for the finished sample")
                .isNotBlank();
        assertThat(report.resultValue())
                .as("the Report screen's result value for the finished sample")
                .isNotBlank();
        assertThat(report.generatedAt())
                .as("the Report screen's generation date for the finished sample")
                .isNotBlank();
    }
}
