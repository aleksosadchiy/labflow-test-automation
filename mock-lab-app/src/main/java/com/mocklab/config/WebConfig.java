package com.mocklab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link SessionAuthInterceptor} against every route, excluding
 * the handful that must stay reachable without a session: the sign-in
 * screen itself, the root redirect, logout, static assets, the
 * {@code /health} readiness check, and everything under {@code /api/**}.
 *
 * <p>The JSON API is guarded by its own access-key filter
 * ({@link ApiKeyFilter}), not by the session. If the session interceptor ran
 * on {@code /api/**}, an API client would be redirected to {@code /login}
 * instead of getting a JSON answer.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/", "/logout", "/static/**", "/health", "/api/**");
    }
}
