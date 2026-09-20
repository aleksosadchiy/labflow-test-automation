package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh application has no samples, so {@code GET /api/samples} returns an
 * empty array (functional-spec.md §2.4).
 *
 * <p>This class needs an application context that no other test has put
 * samples into, but Spring caches identical contexts across test classes.
 * {@code BEFORE_CLASS} throws away any cached context first, so this class
 * always starts on a newly booted, empty application. {@code @DirtiesContext}
 * cannot be repeated on one class, so there is no second, {@code AFTER_CLASS},
 * declaration; none is needed because this class registers no samples, and the
 * context it leaves behind is as empty as it started.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SampleListEmptyApiAcceptanceTest extends AbstractApiAcceptanceTest {

    // @regression
    @Test
    void aFreshApplicationListsNoSamples() throws Exception {
        MvcResult result = mockMvc.perform(withKey(get("/api/samples")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString().trim()).isEqualTo("[]");
    }
}
