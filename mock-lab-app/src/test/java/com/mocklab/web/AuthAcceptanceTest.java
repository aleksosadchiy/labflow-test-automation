package com.mocklab.web;

// @layer: integration
// @spec: 001-mock-lab-application

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Acceptance tests for functional-spec.md §2.1 (Sign In) and the
 * unauthenticated-access-redirect rule that guards every other screen,
 * exercised through the real {@code AuthController} + {@code
 * SessionAuthInterceptor} + {@code WebConfig} wiring (see
 * {@link AbstractWebAcceptanceTest}).
 *
 * @spec: 001-mock-lab-application
 */
class AuthAcceptanceTest extends AbstractWebAcceptanceTest {

    // @regression
    @Test
    void validCredentialsSignInAndRedirectToSampleList() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", SEEDED_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/samples"));
    }

    // @regression
    @Test
    void invalidCredentialsShowErrorAndStayOnSignIn() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Invalid username or password.")));
    }

    // @regression
    @Test
    void blankUsernameShowsRequiredMessage() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "")
                        .param("password", SEEDED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Username and password are required.")));
    }

    // @regression
    @Test
    void blankPasswordShowsRequiredMessage() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Username and password are required.")));
    }

    // @regression
    @Test
    void unauthenticatedRequestToSampleListRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/samples"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // @regression
    @Test
    void unauthenticatedRequestToCreateSampleFormRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/samples/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // @regression
    @Test
    void unauthenticatedRequestToSampleDetailsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/samples/SMP-does-not-matter"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // @regression
    @Test
    void unauthenticatedRequestToReportRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/samples/SMP-does-not-matter/report"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rootRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rootRedirectsToSampleListWhenAuthenticated() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/samples"));
    }

    // @regression
    @Test
    void logoutInvalidatesSessionSoProtectedRoutesRedirectAgain() throws Exception {
        MockHttpSession session = loginAsSeededUser();

        // sanity check: session actually grants access before logout
        mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/samples").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
