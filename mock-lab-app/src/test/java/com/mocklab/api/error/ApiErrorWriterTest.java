package com.mocklab.api.error;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mocklab.exception.FailureKind;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context): drives {@link ApiErrorWriter} with
 * Spring's mock servlet request/response and checks status, content type and
 * the JSON body fields.
 */
class ApiErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiErrorWriter writer = new ApiErrorWriter(objectMapper);

    private MockHttpServletResponse write(MockHttpServletRequest request, HttpStatus status, String message)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.write(request, response, status, message);
        return response;
    }

    // @regression
    @Test
    void writes401WithStandardBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/samples");

        MockHttpServletResponse response = write(request, HttpStatus.UNAUTHORIZED, LabMessages.UNAUTHORIZED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(json.get("message").asText()).isEqualTo("Unauthorized.");
        assertThat(json.get("path").asText()).isEqualTo("/api/samples");
        assertThat(Instant.parse(json.get("timestamp").asText())).isNotNull();
        assertThat(json.size()).isEqualTo(5);
    }

    // @regression
    @Test
    void writes409WithStandardBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/samples");

        MockHttpServletResponse response = write(request, HttpStatus.CONFLICT, LabMessages.DUPLICATE_SAMPLE_TYPE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("error").asText()).isEqualTo("Conflict");
        assertThat(json.get("message").asText()).isEqualTo("A sample of this type already exists for this order.");
        assertThat(json.get("path").asText()).isEqualTo("/api/samples");
    }

    // @regression
    @Test
    void stillWritesJsonWhenClientAcceptsOnlyHtml() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/samples/SMP-1");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);

        MockHttpServletResponse response = write(request, HttpStatus.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("error").asText()).isEqualTo("Not Found");
        assertThat(json.get("message").asText()).isEqualTo("Sample not found.");
    }

    // @regression
    @Test
    void doesNotEchoRequestHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/samples");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer super-secret-key");

        MockHttpServletResponse response = write(request, HttpStatus.UNAUTHORIZED, LabMessages.UNAUTHORIZED);

        assertThat(response.getContentAsString()).doesNotContain("super-secret-key");
    }

    // @regression
    @Test
    void mapsFailureKindToHttpStatus() {
        assertThat(ApiErrorWriter.statusFor(FailureKind.VALIDATION)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiErrorWriter.statusFor(FailureKind.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiErrorWriter.statusFor(FailureKind.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
    }
}
