package com.mocklab.config;

import com.mocklab.web.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Hand-rolled session guard (no Spring Security, per the tech spec's
 * explicit trade-off). Any request reaching this interceptor without the
 * {@link AuthController#AUTHENTICATED_USER_SESSION_ATTRIBUTE} session
 * attribute is redirected to {@code /login} instead of reaching its
 * controller. Routes that must stay reachable while signed out (login,
 * logout, root, static assets) are excluded at registration time in
 * {@link WebConfig}, not here.
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null
                && session.getAttribute(AuthController.AUTHENTICATED_USER_SESSION_ATTRIBUTE) != null;

        if (!authenticated) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
