package com.mocklab.api.error;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct tests of {@link ApiErrorResolver}: it stays out of the way for
 * anything that is not under {@code /api}, and its mappings and logging behave
 * as documented without needing a Spring context.
 */
class ApiErrorResolverTest {

    private final ApiErrorResolver resolver = new ApiErrorResolver(new ApiErrorWriter(new ObjectMapper()));

    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private final Logger resolverLogger = (Logger) LoggerFactory.getLogger(ApiErrorResolver.class);

    @BeforeEach
    void captureLogs() {
        logEvents.start();
        resolverLogger.addAppender(logEvents);
    }

    @AfterEach
    void releaseLogs() {
        resolverLogger.detachAppender(logEvents);
    }

    // @regression
    @Test
    void returnsNullForAnyUriOutsideApiSoTheScreensKeepTheirBehavior() throws Exception {
        for (String uri : List.of("/samples", "/login", "/health", "/", "/apiary", "/nope")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse response = new MockHttpServletResponse();

            ModelAndView result = resolver.resolveException(request, response, null,
                    new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND));

            assertThat(result).as("resolver result for %s", uri).isNull();
            assertThat(response.getContentAsString()).as("body for %s", uri).isEmpty();
            assertThat(response.getStatus()).as("status for %s", uri).isEqualTo(200);
        }
    }

    // @regression
    @Test
    void returnsNullForOutsideApiEvenForUnexpectedFailures() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/samples/S-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolveException(request, response, null, new IllegalStateException("x"))).isNull();
        assertThat(logEvents.list).isEmpty();
    }

    // @regression
    @Test
    void handlesAnApiUriWithAnEmptyModelAndView() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/samples/S-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolver.resolveException(request, response, null,
                new LabRuleException(FailureKind.NOT_FOUND, LabMessages.SAMPLE_NOT_FOUND));

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("\"message\":\"Sample not found.\"");
    }

    // @regression
    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        MockHttpServletResponse response = resolve(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getContentAsString()).contains("\"message\":\"Unsupported media type.\"");
    }

    // @regression
    @Test
    void notAcceptableIs406() throws Exception {
        MockHttpServletResponse response = resolve(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatus()).isEqualTo(406);
        assertThat(response.getContentAsString()).contains("\"message\":\"Not acceptable.\"");
    }

    // @regression
    @Test
    void wrongVerbKeepsTheSupportedMethodsInAnAllowHeader() throws Exception {
        MockHttpServletResponse response = resolve(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "PUT")));

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader(HttpHeaders.ALLOW)).contains("GET").contains("PUT");
    }

    // @regression
    @Test
    void wrongVerbWithoutAKnownMethodListStillAnswers405() throws Exception {
        MockHttpServletResponse response = resolve(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader(HttpHeaders.ALLOW)).isNull();
    }

    // @regression
    @Test
    void unexpectedFailureLogsOnlyTheClassNameMethodAndPathNeverTheMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), "/api/orders");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer super-secret-key");
        request.setContent("{\"name\":\"Jane Doe\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.resolveException(request, response, null,
                new IllegalStateException("Lookup failed for Jane Doe MRN-424242"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString())
                .contains("\"message\":\"Internal server error.\"")
                .doesNotContain("Jane Doe")
                .doesNotContain("IllegalStateException");

        assertThat(logEvents.list).hasSize(1);
        ILoggingEvent event = logEvents.list.get(0);
        assertThat(event.getFormattedMessage())
                .contains("java.lang.IllegalStateException")
                .contains("POST")
                .contains("/api/orders")
                .doesNotContain("Jane Doe")
                .doesNotContain("MRN-424242")
                .doesNotContain("super-secret-key");
        assertThat(event.getThrowableProxy()).as("no stack trace is logged").isNull();
    }

    private MockHttpServletResponse resolve(Exception failure) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ModelAndView result = resolver.resolveException(request, response, null, failure);
        assertThat(result).isNotNull();
        return response;
    }
}
