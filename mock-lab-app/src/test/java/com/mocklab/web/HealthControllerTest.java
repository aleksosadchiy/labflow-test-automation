package com.mocklab.web;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link HealthController} (functional-spec.md §2.9,
 * technical-considerations.md §4 "Readiness"). {@code @WebMvcTest} loads only
 * the web layer (this controller plus {@code WebConfig}'s session
 * interceptor), with {@link ApplicationAvailability} mocked so both readiness
 * states can be driven deterministically. That is the only way to observe the
 * "not ready yet" answer, because in a real boot the port opens before the
 * seeder has run and the window is too short to hit reliably.
 *
 * <p>No session or credentials are sent on any request, so a 200/503 (rather
 * than a 302 to {@code /login}) also proves {@code /health} is excluded from
 * the session interceptor.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ApplicationAvailability availability;

    // @regression
    @Test
    void refusingTrafficReturns503Starting() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"STARTING\"}", true));
    }

    // @regression
    @Test
    void acceptingTrafficReturns200Up() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }
}
