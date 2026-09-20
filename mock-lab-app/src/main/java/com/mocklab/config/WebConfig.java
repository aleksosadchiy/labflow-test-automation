package com.mocklab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link SessionAuthInterceptor} against every route, excluding
 * the handful that must stay reachable without a session: the sign-in
 * screen itself, the root redirect, logout, static assets, and the
 * {@code /health} readiness check.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/", "/logout", "/static/**", "/health");
    }
}
