// @layer: e2e
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.rejection;

import com.labflow.common.LabMessages;
import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sample Details screen refuses an id the lab does not know.
 */
class SampleDetailsRejectionTest extends UiTest {

    @Test
    @DisplayName("Opening the details of a sample id the lab does not know shows 'Sample not found.'")
    void unknownSampleIsNotFound() {
        signedIn();
        String unknownSampleId = UniqueData.name("SMP-unknown");

        SampleDetailsPage details = SampleDetailsPage.openExpectingNotFound(page, config, unknownSampleId);

        assertThat(details.notFoundMessage())
                .as("the Details screen's message for a sample id the lab does not know")
                .isEqualTo(LabMessages.SAMPLE_NOT_FOUND);
    }
}
