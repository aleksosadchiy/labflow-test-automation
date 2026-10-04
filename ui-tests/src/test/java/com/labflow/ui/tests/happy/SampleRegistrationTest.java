// @layer: e2e
// @spec: 004-ui-automation-page-objects
package com.labflow.ui.tests.happy;

import com.labflow.common.junit.Smoke;
import com.labflow.common.model.Patient;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.pages.SampleListPage;
import com.labflow.ui.pages.SampleRow;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registering a sample for a known patient on the Create Sample screen puts it straight into
 * the workflow, and it shows up in the shared Sample List with that patient and "Accessioned".
 */
class SampleRegistrationTest extends UiTest {

    @Test
    @Smoke
    @DisplayName("A sample registered on screen for a known patient appears in the list as Accessioned")
    void registeredSampleIsListedAsAccessioned() {
        Patient patient = newPatient();

        SampleDetailsPage details = signedIn().startNewSample()
                .createSample(patient.id(), SampleType.BLOOD.name());
        String sampleId = details.sampleId();

        Optional<SampleRow> row = SampleListPage.open(page, config).rowFor(sampleId);

        assertThat(row)
                .as("whether the Sample List shows the newly registered sample")
                .isPresent();
        assertThat(row.get().patientName())
                .as("the patient name shown in the new sample's row")
                .isEqualTo(patient.name());
        assertThat(row.get().status())
                .as("the status shown in the new sample's row")
                .isEqualTo(WorkflowStage.ACCESSIONED);
    }
}
