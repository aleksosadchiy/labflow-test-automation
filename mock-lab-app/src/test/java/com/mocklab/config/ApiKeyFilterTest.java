package com.mocklab.config;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mocklab.api.error.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context): drives {@link ApiKeyFilter} with Spring's
 * mock servlet request/response/chain. The chain records whether it was invoked,
 * which is how "the request was let through" vs "the request was stopped" is
 * observed.
 */
class ApiKeyFilterTest {

    private static final String TOKEN = "unit-test-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiKeyFilter filter = new ApiKeyFilter(TOKEN, new ApiErrorWriter(objectMapper));

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/samples");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    private void run(String authorizationHeader) throws Exception {
        if (authorizationHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        filter.doFilter(request, response, chain);
    }

    private void assertRejectedAsUnauthorized() throws Exception {
        assertThat(chain.getRequest()).as("chain must not be invoked").isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("message").asText()).isEqualTo("Unauthorized.");
        assertThat(json.get("path").asText()).isEqualTo("/api/samples");
    }

    // @regression
    @Test
    void correctKeyContinuesTheChain() throws Exception {
        run("Bearer " + TOKEN);

        assertThat(chain.getRequest()).as("chain must be invoked").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"bearer ", "BEARER ", "bEaReR "})
    void bearerSchemeIsCaseInsensitive(String scheme) throws Exception {
        run(scheme + TOKEN);

        assertThat(chain.getRequest()).as("chain must be invoked").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // @regression
    @Test
    void missingHeaderIsUnauthorized() throws Exception {
        run(null);

        assertRejectedAsUnauthorized();
    }

    // @regression
    @Test
    void wrongKeyIsUnauthorized() throws Exception {
        run("Bearer not-the-token");

        assertRejectedAsUnauthorized();
    }

    // @regression
    @Test
    void otherSchemeIsUnauthorizedEvenWithTheRightKey() throws Exception {
        run("Basic " + TOKEN);

        assertRejectedAsUnauthorized();
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"Bearer ", "Bearer    ", "Bearer", ""})
    void blankKeyIsUnauthorized(String header) throws Exception {
        run(header);

        assertRejectedAsUnauthorized();
    }

    // @regression
    @Test
    void keyThatOnlySharesAPrefixWithTheTokenIsUnauthorized() throws Exception {
        run("Bearer " + TOKEN.substring(0, TOKEN.length() - 1));

        assertRejectedAsUnauthorized();
    }

    // @regression
    @Test
    void neverCreatesASession() throws Exception {
        run("Bearer wrong");
        assertThat(request.getSession(false)).isNull();

        MockHttpServletRequest okRequest = new MockHttpServletRequest("GET", "/api/samples");
        okRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        filter.doFilter(okRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(okRequest.getSession(false)).isNull();
    }

    // @regression
    @Test
    void presentedKeyIsNeverEchoedInTheResponse() throws Exception {
        String presented = "presented-secret-guess";

        run("Bearer " + presented);

        assertRejectedAsUnauthorized();
        assertThat(response.getContentAsString()).doesNotContain(presented).doesNotContain(TOKEN);
        assertThat(response.getHeaderNames()).allSatisfy(name ->
                assertThat(response.getHeaders(name)).allSatisfy(value ->
                        assertThat(value).doesNotContain(presented).doesNotContain(TOKEN)));
    }
}
