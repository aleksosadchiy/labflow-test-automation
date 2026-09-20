package com.mocklab.api.error;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the test suite itself: the test-only controller used by
 * {@link ApiErrorMappingIntegrationTest} must not leak into the application
 * context of any other test class. This class does not import it, so if a
 * component scan ever picked it up the assertions below would fail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiErrorTestSupportIsolationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mockMvc;

    // @regression
    @Test
    void theTestOnlyControllerIsNotPartOfTheDefaultContext() throws Exception {
        assertThat(context.getBeanNamesForType(ApiErrorTestSupportController.class)).isEmpty();

        mockMvc.perform(get("/api/test-support/get-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer mock-lab-local-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }
}
