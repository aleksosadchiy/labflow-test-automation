package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
}
