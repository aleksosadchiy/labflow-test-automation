package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared scaffolding for the REST API acceptance tests. Boots the real Spring
 * context once per test class (no {@code @DirtiesContext}) and sends requests
 * over MockMvc.
 *
 * <p>Because the context, and so the in-memory data, is shared by every test in
 * a class, tests must create their own UUID-based data and never assume a
 * clean slate or the order they run in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
abstract class AbstractApiAcceptanceTest {

    /** The documented local default of {@code mock-lab.api.token}. */
    static final String API_KEY = "mock-lab-local-token";

    @Autowired
    MockMvc mockMvc;

    /** Adds the {@code Authorization: Bearer <key>} header every {@code /api/**} call needs. */
    static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY);
    }

    /** Asserts the standard error body: every field present, with the expected status, message and path. */
    static void expectError(MvcResult result, int status, String error, String message, String path)
            throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(status);
        assertThat((String) JsonPath.read(body, "$.error")).isEqualTo(error);
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo(message);
        assertThat((String) JsonPath.read(body, "$.path")).isEqualTo(path);
        assertThat((String) JsonPath.read(body, "$.timestamp")).isNotBlank();
    }

    static void expectError(ResultActions result, int status, String error, String message, String path)
            throws Exception {
        expectError(result.andReturn(), status, error, message, path);
    }
}
