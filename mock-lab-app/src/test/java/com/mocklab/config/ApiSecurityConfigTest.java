package com.mocklab.config;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mocklab.api.error.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts only {@link ApiSecurityConfig} in a bare application context (no web
 * server, no full app) to prove the fail-fast rule and the registration shape.
 * Using {@code ApplicationContextRunner} keeps this fast and lets the failed
 * start-up be inspected instead of crashing the test JVM.
 */
class ApiSecurityConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApiSecurityConfig.class)
            .withBean(ApiErrorWriter.class, () -> new ApiErrorWriter(new ObjectMapper()));

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankTokenStopsStartupWithAClearMessage(String blankToken) {
        runner.withPropertyValues("mock-lab.api.token=" + blankToken).run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = rootCauseOf(context.getStartupFailure());
            assertThat(rootCause).isInstanceOf(IllegalStateException.class);
            assertThat(rootCause.getMessage())
                    .contains("API_TOKEN")
                    .contains("blank")
                    .contains("Refusing to start");
        });
    }

    // @regression
    @Test
    void failureMessageNeverContainsTheTokenValue() {
        // Whitespace-only tokens are the one blank case with a "value"; make sure it is not printed.
        String whitespaceToken = " \t ";
        runner.withPropertyValues("mock-lab.api.token=" + whitespaceToken).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseOf(context.getStartupFailure()).getMessage()).doesNotContain(whitespaceToken);
        });
    }

    // @regression
    @Test
    void nonBlankTokenStartsAndRegistersTheFilterForApiOnly() {
        runner.withPropertyValues("mock-lab.api.token=some-token").run(context -> {
            assertThat(context).hasNotFailed();
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<ApiKeyFilter> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
            assertThat(registration.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
            assertThat(registration.getFilter()).isInstanceOf(ApiKeyFilter.class);
        });
    }

    private static Throwable rootCauseOf(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
