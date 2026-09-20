package com.mocklab.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mocklab.exception.FailureKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the standard {@link ApiErrorResponse} straight to the servlet
 * response. It is shared by the API-key filter (which rejects before Spring MVC
 * dispatch, so no message converters or {@code Accept} negotiation apply) and by
 * the exception resolver, so the body is always JSON no matter what the client
 * asked for.
 *
 * <p>Only the status, reason phrase, message and request path are written: no
 * stack traces, no request headers, no credentials.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Maps a business-rule failure category to its HTTP status. */
    public static HttpStatus statusFor(FailureKind kind) {
        return switch (kind) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String message) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Instant.now().toString());

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }
}
