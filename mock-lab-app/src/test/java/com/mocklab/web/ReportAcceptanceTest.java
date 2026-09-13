package com.mocklab.web;

// @layer: integration
// @spec: 001-mock-lab-application

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Acceptance tests for functional-spec.md §2.5 (View Report), exercised
 * independently of the full 8-stage happy-path journey in {@link
 * SampleWorkflowAcceptanceTest} so this requirement area has its own
 * dedicated, isolated coverage (see {@link AbstractWebAcceptanceTest} for
 * why each test gets a fresh Spring context/repositories).
 *
 * @spec: 001-mock-lab-application
 */
class ReportAcceptanceTest extends AbstractWebAcceptanceTest {

    // @regression
    @Test
    void reportForUnknownSampleShowsNotFoundMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(get("/samples/SMP-does-not-exist/report").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(content().string(containsString("Sample not found.")));
    }

    // @regression
    @Test
    void reportBeforeCompletionShowsNotReadyMessage() throws Exception {
        MockHttpSession session = loginAsSeededUser();
        String sampleId = registerSample(session);

        // freshly created sample is at ACCESSIONED, nowhere near REPORT_GENERATED
        mockMvc.perform(get("/samples/" + sampleId + "/report").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(content().string(containsString("Report is not ready yet.")))
                .andExpect(content().string(not(containsString("id=\"report-test-name\""))));
    }

    // @regression
    @Test
    void reportForCompletedSampleShowsSampleIdTestNameResultValueAndGeneratedDate() throws Exception {
        MockHttpSession session = loginAsSeededUser();
        String sampleId = registerSample(session);

        // ACCESSIONED -> ... -> REPORT_GENERATED is 7 advances (8 stages total)
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(post("/samples/" + sampleId + "/advance").session(session));
        }

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
    void unauthenticatedRequestToReportRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/samples/SMP-anything/report"))
                .andExpect(status().is3xxRedirection());
    }

    private String registerSample(MockHttpSession session) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", KNOWN_PATIENT_ID)
                        .param("sampleType", "BLOOD"))
                .andReturn();
        String redirectedUrl = createResult.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        return redirectedUrl.substring("/samples/".length());
    }
}
