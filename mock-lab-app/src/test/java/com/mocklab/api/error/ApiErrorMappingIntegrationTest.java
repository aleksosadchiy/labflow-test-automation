package com.mocklab.api.error;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, over MockMvc in the real application context, that every failure on
 * {@code /api/**} comes back as the standard JSON error body with the right
 * status and message, and that the screens are not affected
 * (technical-considerations.md §2.1 "Error mapping" and the risk row that
 * requires the resolver's cases to be proven first).
 *
 * <p>No real API controllers exist yet, so the test-only
 * {@link ApiErrorTestSupportController} supplies routes that throw each kind of
 * failure. It is imported explicitly here and nowhere else.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(ApiErrorTestSupportController.class)
class ApiErrorMappingIntegrationTest {

    private static final String BEARER = "Bearer mock-lab-local-token";

    @Autowired
    MockMvc mockMvc;

    // ---- the four cases the risk table asks to prove first ---------------------------------

    // @regression
    @Test
    void unknownRouteWithKeyIsNotFoundInTheStandardBody() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Resource not found."))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // @regression
    @ParameterizedTest
    @CsvSource({
            "DELETE, /api/does-not-exist",
            "POST,   /api/does-not-exist",
            "PUT,    /api/does-not-exist",
            "GET,    /api/",
            "GET,    /api"
    })
    void anUnknownRouteIsNotFoundWhateverTheVerbOrTrailingSlash(String method, String path) throws Exception {
        mockMvc.perform(request(method, path).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    // @regression
    @Test
    void brokenJsonBodyIsMalformedAndNeverEchoesThePayload() throws Exception {
        String brokenPayload = "{\"name\": \"" + ApiErrorTestSupportController.SECRET_TEXT + "\", ";

        MvcResult result = mockMvc.perform(post("/api/test-support/echo")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brokenPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed request body."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Jane Doe").doesNotContain("MRN-424242");
    }

    // @regression
    @Test
    void wrongShapeBodyIsAlsoMalformed() throws Exception {
        mockMvc.perform(post("/api/test-support/echo")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\", \"count\": \"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    // @regression
    @Test
    void wrongVerbIsMethodNotAllowedWithAnAllowHeader() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/test-support/get-only")
                        .header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value("Method not allowed."))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
    }

    // @regression
    @ParameterizedTest
    @CsvSource({
            "VALIDATION, 400, Bad Request,  'Patient name is required.'",
            "NOT_FOUND,  404, Not Found,    'Sample not found.'",
            "CONFLICT,   409, Conflict,     'A sample of this type already exists for this order.'"
    })
    void eachFailureKindMapsToItsStatusAndCarriesTheExceptionMessage(
            String kind, int expectedStatus, String expectedReason, String expectedMessage) throws Exception {
        mockMvc.perform(get("/api/test-support/fail/" + kind).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.error").value(expectedReason))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value("/api/test-support/fail/" + kind))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // ---- the cases added beyond the functional spec ----------------------------------------

    // @regression
    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mockMvc.perform(post("/api/test-support/echo")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("Unsupported media type."));
    }

    // @regression
    @Test
    void notAcceptableIs406AndStillJson() throws Exception {
        mockMvc.perform(get("/api/test-support/json-only")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.message").value("Not acceptable."));
    }

    // @regression
    @Test
    void unexpectedFailureIs500WithoutExceptionTextOrStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test-support/boom").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("Jane Doe")
                .doesNotContain("MRN-424242")
                .doesNotContain("IllegalStateException")
                .doesNotContain("Lookup failed")
                .doesNotContain("\tat ")
                .doesNotContain("com.mocklab");
    }

    // @regression
    @Test
    void errorBodyIsJsonEvenWhenTheClientAsksForHtml() throws Exception {
        mockMvc.perform(get("/api/test-support/fail/NOT_FOUND")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Sample not found."));

        mockMvc.perform(get("/api/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    // ---- 401 wins over every other rejection ------------------------------------------------

    // @regression
    @ParameterizedTest
    @CsvSource({
            "GET,    /api/does-not-exist",
            "GET,    /api/samples/nope",
            "DELETE, /api/samples/nope",
            "DELETE, /api/test-support/get-only",
            "POST,   /api/test-support/echo"
    })
    void withoutAKeyEveryRejectionIsUnauthorized(String method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized."));
    }

    // ---- API calls are not sent through the screens' login redirect ------------------------

    // @regression
    @Test
    void validKeyOnAnApiRouteIsNeverRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/api/test-support/get-only").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("true"));

        mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    // ---- the screens keep their behavior ----------------------------------------------------

    // @regression
    @Test
    void screensStillRedirectUnauthenticatedUsersToLogin() throws Exception {
        mockMvc.perform(get("/samples"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/nope"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    // @regression
    @Test
    void loginAndHealthAreStillOpen() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        return MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
    }
}
