package com.mocklab.config;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the key filter's registration scope in the real application context:
 * it guards {@code /api/*} and nothing else. It deliberately does not assert
 * what happens to {@code /api/**} <em>with</em> a valid key; the 404 mapping for
 * unknown routes is not built yet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiKeyRegistrationScopeIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // @regression
    @Test
    void apiRouteWithoutKeyIsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized."))
                .andExpect(jsonPath("$.path").value("/api/anything"));
    }

    // @regression
    @Test
    void healthIsNotAffectedByTheKeyFilter() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // @regression
    @Test
    void loginPageIsNotAffectedByTheKeyFilter() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    // @regression
    @Test
    void screenRouteStillRedirectsToLoginRatherThanRequiringAKey() throws Exception {
        mockMvc.perform(get("/samples"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }
}
