package com.mocklab.web;

// @layer: integration
// @spec: 001-mock-lab-application

import com.mocklab.domain.SampleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Acceptance tests exercising the feature end-to-end across
 * functional-spec.md §2.2 (View Sample List), §2.3 (Register a New Sample),
 * and §2.4 (View Sample Details & Workflow Progress) as a whole — not any
 * one implementation slice. The main {@link
 * #fullHappyPathJourneyThroughAllEightStages()} test drives one sample from
 * registration through every one of the 8 workflow stages, asserting the
 * screens and model state a real user (or the future UI test-automation
 * suite) would observe at each step.
 *
 * @spec: 001-mock-lab-application
 */
class SampleWorkflowAcceptanceTest extends AbstractWebAcceptanceTest {

    // @regression
    @Test
    void fullHappyPathJourneyThroughAllEightStages() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        // 1. Sample List starts empty (functional-spec.md §2.2)
        mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("sample-list"))
                .andExpect(content().string(containsString("No samples registered yet.")));

        // 2. Register a new sample (functional-spec.md §2.3)
        MvcResult createResult = mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", KNOWN_PATIENT_ID)
                        .param("sampleType", "BLOOD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/samples/*"))
                .andReturn();

        String redirectedUrl = createResult.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        String sampleId = redirectedUrl.substring("/samples/".length());
        assertThat(sampleId).isNotBlank();

        // 3. The new sample now appears in the Sample List with status Accessioned
        mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sampleId)))
                .andExpect(content().string(containsString(KNOWN_PATIENT_NAME)))
                .andExpect(content().string(containsString(SampleStatus.ACCESSIONED.name())));

        // 4. Sample Details shows all 8 stages, current = ACCESSIONED, Advance button present
        mockMvc.perform(get("/samples/" + sampleId).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("sample-details"))
                .andExpect(content().string(not(containsString("Sample not found."))))
                .andExpect(content().string(containsString(SampleStatus.ACCESSIONED.name())))
                .andExpect(content().string(containsString("id=\"advance-button\"")))
                .andExpect(content().string(not(containsString("id=\"view-report-link\""))));

        SampleStatus[] allStatuses = SampleStatus.values();

        // 5. Advance through every remaining stage; assert current stage + button visibility each time
        for (int i = 1; i < allStatuses.length; i++) {
            SampleStatus expectedStatus = allStatuses[i];

            mockMvc.perform(post("/samples/" + sampleId + "/advance").session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/samples/" + sampleId));

            boolean isFinalStage = expectedStatus == SampleStatus.REPORT_GENERATED;

            var detailsExpectation = mockMvc.perform(get("/samples/" + sampleId).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "id=\"current-status\">" + expectedStatus.name())));

            if (isFinalStage) {
                detailsExpectation
                        .andExpect(content().string(not(containsString("id=\"advance-button\""))))
                        .andExpect(content().string(containsString("id=\"view-report-link\"")));
            } else {
                detailsExpectation
                        .andExpect(content().string(containsString("id=\"advance-button\"")))
                        .andExpect(content().string(not(containsString("id=\"view-report-link\""))));
            }
        }

        // 6. Final stage reached: view the report (functional-spec.md §2.5)
        mockMvc.perform(get("/samples/" + sampleId + "/report").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(content().string(not(containsString("Report is not ready yet."))))
                .andExpect(content().string(containsString(sampleId)))
                .andExpect(content().string(containsString("id=\"report-test-name\"")))
                .andExpect(content().string(containsString("id=\"report-result-value\"")))
                .andExpect(content().string(containsString("id=\"report-generated-at\"")));
    }

    // @regression
    @Test
    void createSampleWithBlankPatientShowsRequiredMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", "")
                        .param("sampleType", "BLOOD"))
                .andExpect(status().isOk())
                .andExpect(view().name("create-sample"))
                .andExpect(content().string(containsString("Patient is required.")));
    }

    // @regression
    @Test
    void createSampleWithBlankSampleTypeShowsRequiredMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", KNOWN_PATIENT_ID)
                        .param("sampleType", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("create-sample"))
                .andExpect(content().string(containsString("Sample type is required.")));
    }

    // @regression
    @Test
    void createSampleWithUnknownPatientShowsNotFoundMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", "PAT-9999")
                        .param("sampleType", "BLOOD"))
                .andExpect(status().isOk())
                .andExpect(view().name("create-sample"))
                .andExpect(content().string(containsString("Patient not found.")));
    }

    // @regression
    @Test
    void createSampleWithInvalidSampleTypeShowsInvalidMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", KNOWN_PATIENT_ID)
                        .param("sampleType", "PLASMA"))
                .andExpect(status().isOk())
                .andExpect(view().name("create-sample"))
                .andExpect(content().string(containsString("Invalid sample type.")));
    }

    // @regression
    @Test
    void sampleDetailsForUnknownIdShowsNotFoundMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(get("/samples/SMP-does-not-exist").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("sample-details"))
                .andExpect(content().string(containsString("Sample not found.")));
    }

    // @regression
    @Test
    void advancingUnknownSampleIdRedirectsAndThenShowsNotFound() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(post("/samples/SMP-does-not-exist/advance").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/samples/SMP-does-not-exist"));

        mockMvc.perform(get("/samples/SMP-does-not-exist").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample not found.")));
    }
}
