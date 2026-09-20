package com.mocklab.config;

import com.mocklab.api.error.ApiErrorWriter;
import com.mocklab.api.error.LabMessages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Access-key guard for the JSON API (technical-considerations.md §2.3).
 *
 * <p>Requires {@code Authorization: Bearer <key>}. The scheme is matched
 * case-insensitively, a blank key is invalid, and the key is compared with the
 * configured token in constant time ({@link MessageDigest#isEqual} on UTF-8
 * bytes), so response timing does not reveal how much of a guess was right.
 * Anything else (no header, another scheme, blank key, wrong key) is answered
 * with 401 {@link LabMessages#UNAUTHORIZED} plus {@code WWW-Authenticate: Bearer},
 * and the chain is not continued.
 *
 * <p>Deliberately stateless and quiet: it never calls {@code getSession()} (the
 * API must not create or read a session, so a screen sign-in cannot open the
 * API), never logs the header or the token, and never echoes the presented key.
 *
 * <p>This class is intentionally not a Spring bean. {@link ApiSecurityConfig}
 * constructs it and registers it for {@code /api/*} only; exposing it as a
 * {@code Filter} bean would make Spring Boot apply it to every URL.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;
    private final ApiErrorWriter errorWriter;

    public ApiKeyFilter(String token, ApiErrorWriter errorWriter) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, LabMessages.UNAUTHORIZED);
    }

    private boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        String presentedKey = authorizationHeader.substring(BEARER_PREFIX.length()).strip();
        if (presentedKey.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(presentedKey.getBytes(StandardCharsets.UTF_8), expectedToken);
    }
}
