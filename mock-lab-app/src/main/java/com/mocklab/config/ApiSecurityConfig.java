package com.mocklab.config;

import com.mocklab.api.error.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires the API access key: reads the token, refuses to start if it is blank,
 * and registers {@link ApiKeyFilter} for {@code /api/*} only.
 *
 * <p>Registration is explicit (a {@link FilterRegistrationBean}) rather than
 * by exposing the filter as a bean, so nothing outside {@code /api} - the
 * screens, {@code /health}, static assets - is ever touched by it.
 *
 * <p>The token comes from {@code mock-lab.api.token}, which {@code application.yml}
 * resolves from the {@code API_TOKEN} environment variable with a documented,
 * local-only default. An empty {@code API_TOKEN=} resolves to an empty string
 * (it overrides the default), which would silently make the check meaningless,
 * hence the fail-fast below.
 */
@Configuration
public class ApiSecurityConfig {

    /**
     * Runs ahead of the servlet and Spring MVC dispatch, and ahead of the other
     * application filters. The filter has to decide 401 before any 404 (unknown
     * route) or 405 (wrong verb) can be produced, so "no key" always wins over
     * every other rejection (functional-spec.md section 2.1). It must also run
     * before anything that could touch the session.
     */
    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    static final String API_URL_PATTERN = "/api/*";

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration(
            @Value("${mock-lab.api.token}") String token,
            ApiErrorWriter errorWriter) {
        requireNonBlank(token);

        FilterRegistrationBean<ApiKeyFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyFilter(token, errorWriter));
        registration.addUrlPatterns(API_URL_PATTERN);
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    /** The message deliberately never includes the token value. */
    private static void requireNonBlank(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "The API access key is blank: set the API_TOKEN environment variable "
                            + "(property mock-lab.api.token) to a non-blank value. Refusing to start "
                            + "with the API unprotected.");
        }
    }
}
