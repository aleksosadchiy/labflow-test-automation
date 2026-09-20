package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of functional-spec.md section 2.5 (Moving a Sample Through the
 * Workflow), specifically the one criterion the slice tests only proved for one of its two
 * screens: after a client moves a sample forward, the new stage is shown on the Sample List
 * <em>and</em> Sample Details screens. (The Sample List reads the sample's own status while
 * the API drives the processing job, so this proves the two stay in step.)
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>Accessioned -&gt; Ready for Processing accepted, sample shown with the new status.</b>
 *       {@code SampleStatusApiAcceptanceTest#theNextStageIsAcceptedUnderItsDisplayNameOrInAnyLetterCaseAndReturnedAsTheEnumName},
 *       {@code #aSampleCanBeWalkedThroughEveryStageOneAcceptedPutAtATime}.</li>
 *   <li><b>One request per stage: every request accepted, ends at "Report Generated".</b>
 *       {@code SampleStatusApiAcceptanceTest#aSampleCanBeWalkedThroughEveryStageOneAcceptedPutAtATime},
 *       {@code LabWorkflowEndToEndAcceptanceTest#aLabWorkflowRunsFromPatientToReportThroughTheApiAndIsSeenOnTheScreens}.</li>
 *   <li><b>Processing -&gt; Result Ready: "Invalid status transition: cannot move from Processing to Result Ready.",
 *       stays at Processing.</b>
 *       {@code SampleStatusApiAcceptanceTest#skippingAheadFromProcessingToResultReadyIsRejectedAndTheSampleStaysPut}.</li>
 *   <li><b>Result Ready -&gt; Accessioned: "Invalid status transition: cannot move from Result Ready to Accessioned.",
 *       stays at Result Ready.</b>
 *       {@code SampleStatusApiAcceptanceTest#movingBackwardIsRejectedAndTheSampleStaysPut}.</li>
 *   <li><b>Same stage: invalid transition, status unchanged.</b>
 *       {@code SampleStatusApiAcceptanceTest#repeatingThePutOfAStageTheSampleAlreadyReachedIsRejectedAndTheSampleStaysPut}.</li>
 *   <li><b>From "Report Generated" to any stage: invalid transition.</b>
 *       {@code SampleStatusApiAcceptanceTest#noTargetIsAcceptedOnceTheSampleHasReachedReportGenerated} (all 8 targets).</li>
 *   <li><b>Unknown stage name: "Invalid status."</b>
 *       {@code SampleStatusApiAcceptanceTest#anUnknownStatusIsRejectedAndTheSampleStaysPut}.</li>
 *   <li><b>No stage named: "Status is required."</b>
 *       {@code SampleStatusApiAcceptanceTest#aNullMissingEmptyOrBlankStatusIsRejectedAndTheSampleStaysPut}.</li>
 *   <li><b>Unknown sample: "Sample not found."</b>
 *       {@code SampleStatusApiAcceptanceTest#anUnknownSampleWithAValidStatusIsNotFound}.</li>
 *   <li><b>Moving forward shows the new stage on the Sample List and Sample Details screens.</b>
 *       Details: {@code SampleStatusApiAcceptanceTest#aStageSetThroughTheApiIsShownOnTheSampleDetailsScreen}
 *       (one stage). List, and every stage on both screens:
 *       {@link #everyStageReachedThroughTheApiIsShownOnTheSampleListAndSampleDetailsScreens}.
 *       Negative counterpart: {@link #aRejectedMoveLeavesBothScreensAtTheOldStage}.</li>
 * </ol>
 */
class Spec2_5WorkflowScreensAcceptanceTest extends AbstractSpecAcceptanceTest {

    // @regression
    @Test
    void everyStageReachedThroughTheApiIsShownOnTheSampleListAndSampleDetailsScreens() throws Exception {
        String id = read(registerSample("PAT-1001", "BLOOD"), "$.id");
        MockHttpSession session = signIn();
        assertScreensShow(session, id, "ACCESSIONED");

        for (int i = 1; i < STAGES.size(); i++) {
            mockMvc.perform(putStatus(id, STAGES.get(i))).andExpect(status().isOk());

            assertScreensShow(session, id, STAGES.get(i));
        }
    }

    // @regression
    @Test
    void aRejectedMoveLeavesBothScreensAtTheOldStage() throws Exception {
        String id = read(registerSample("PAT-1001", "BLOOD"), "$.id");
        walkTo(id, "PROCESSING");
        MockHttpSession session = signIn();

        // Processing -> Result Ready skips two stages, and Processing -> Accessioned goes back.
        mockMvc.perform(putStatus(id, "RESULT_READY")).andExpect(status().isConflict());
        mockMvc.perform(putStatus(id, "ACCESSIONED")).andExpect(status().isConflict());

        assertScreensShow(session, id, "PROCESSING");
    }

    /** The Sample List row and the Sample Details page for the sample both show exactly {@code stage}. */
    private void assertScreensShow(MockHttpSession session, String sampleId, String stage) throws Exception {
        String list = mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String row = rowFor(list, sampleId);
        assertThat(row).as("Sample List row for %s", sampleId).contains(">" + stage + "<");

        String details = mockMvc.perform(get("/samples/" + sampleId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(details).as("Sample Details for %s", sampleId)
                .containsPattern("id=\"current-status\"[^>]*>" + stage + "<");
    }
}
