package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional-spec.md section 2.9: "When the lab is started with a chosen access key, then only
 * that key is accepted." The whole application is started with {@code mock-lab.api.token} set
 * to a chosen key (the property that {@code application.yml} fills from the {@code API_TOKEN}
 * environment variable), and requests are sent through the real filter registration.
 *
 * <p>The other half of the criterion, "when none is chosen, a documented default key for local use is
 * accepted", is {@code Spec2_1AccessControlAcceptanceTest#theDocumentedLocalDefaultKeyIsAcceptedWhenNoKeyIsChosen},
 * and the mapping of every 2.9 criterion is in {@code Spec2_9StartupStateAcceptanceTest}. A lab started with a
 * blank key refuses to start ({@code ApiSecurityConfigTest}).
 *
 * <p>The property is the same one the {@code API_TOKEN} environment variable ends up in, so this proves the
 * behavior; that the variable name maps onto it is one line of {@code application.yml}, exercised by the
 * Slice 2 "Verify" task in tasks.md ({@code API_TOKEN=custom}) as a real process start.
 */
@TestPropertySource(properties = "mock-lab.api.token=" + Spec2_9AccessKeyConfigurationAcceptanceTest.CHOSEN_KEY)
class Spec2_9AccessKeyConfigurationAcceptanceTest extends AbstractSpecAcceptanceTest {

    static final String CHOSEN_KEY = "spec2-9-chosen-key";

    // @regression
    @Test
    void onlyTheChosenKeyIsAcceptedAndTheDocumentedDefaultIsNot() throws Exception {
        // the chosen key reads and changes lab records
        mockMvc.perform(get("/api/patients/PAT-1001").header(HttpHeaders.AUTHORIZATION, "Bearer " + CHOSEN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Johnson"));
        mockMvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + CHOSEN_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Chosen key\"}"))
                .andExpect(status().isCreated());

        // the default key of a lab that was not given one is now just a wrong key
        expectError(mockMvc.perform(get("/api/patients/PAT-1001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)),
                401, "Unauthorized", "Unauthorized.", "/api/patients/PAT-1001");
        expectError(mockMvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Default key\"}")),
                401, "Unauthorized", "Unauthorized.", "/api/patients");

        // and, as ever, no key at all is not accepted either
        expectError(mockMvc.perform(get("/api/patients/PAT-1001")),
                401, "Unauthorized", "Unauthorized.", "/api/patients/PAT-1001");
    }
}
