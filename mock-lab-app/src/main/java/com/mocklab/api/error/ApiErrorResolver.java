package com.mocklab.api.error;

import com.mocklab.exception.LabRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place every failure on {@code /api/**} becomes the standard JSON
 * error body (technical-considerations.md §2.1, "Error mapping").
 *
 * <p>This is a {@link HandlerExceptionResolver} rather than a
 * {@code @RestControllerAdvice} because two of the cases have no handler
 * method for an advice to belong to: an unknown route (404) and a wrong verb
 * (405) are raised by Spring MVC before or instead of any controller running,
 * so a controller advice does not reliably see them. A resolver registered at
 * highest precedence sees every exception the dispatcher raises.
 *
 * <p>It is active only for request URIs starting with {@code /api}; for any
 * other URI it returns {@code null} ("not handled") so the screens keep the
 * behavior they already had. The body is always JSON, regardless of the
 * request's {@code Accept} header, and is written through
 * {@link ApiErrorWriter}, the same writer the key filter uses for its 401.
 *
 * <p>Nothing from the failing request is echoed back or logged: a malformed
 * body gets a fixed message (Jackson's own message can quote the payload) and
 * an unexpected failure gets a fixed message with no stack trace. The 500 path
 * logs only the exception class name and the request method and path, because
 * exception messages can carry patient data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorResolver.class);

    private static final String API_PREFIX = "/api";

    private final ApiErrorWriter errorWriter;

    public ApiErrorResolver(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        if (!isApiRequest(request)) {
            return null;
        }
        try {
            if (ex instanceof LabRuleException rule) {
                errorWriter.write(request, response, ApiErrorWriter.statusFor(rule.kind()), rule.getMessage());
            } else if (ex instanceof HttpMessageNotReadableException) {
                errorWriter.write(request, response, HttpStatus.BAD_REQUEST, LabMessages.MALFORMED_REQUEST_BODY);
            } else if (ex instanceof HttpRequestMethodNotSupportedException wrongVerb) {
                Set<HttpMethod> allowed = wrongVerb.getSupportedHttpMethods();
                if (allowed != null && !allowed.isEmpty()) {
                    response.setHeader(HttpHeaders.ALLOW,
                            allowed.stream().map(HttpMethod::name).collect(Collectors.joining(", ")));
                }
                errorWriter.write(request, response, HttpStatus.METHOD_NOT_ALLOWED, LabMessages.METHOD_NOT_ALLOWED);
            } else if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
                errorWriter.write(request, response, HttpStatus.NOT_FOUND, LabMessages.RESOURCE_NOT_FOUND);
            } else if (ex instanceof HttpMediaTypeNotSupportedException) {
                errorWriter.write(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        LabMessages.UNSUPPORTED_MEDIA_TYPE);
            } else if (ex instanceof HttpMediaTypeNotAcceptableException) {
                errorWriter.write(request, response, HttpStatus.NOT_ACCEPTABLE, LabMessages.NOT_ACCEPTABLE);
            } else {
                log.error("Unexpected {} while handling {} {}",
                        ex.getClass().getName(), request.getMethod(), request.getRequestURI());
                errorWriter.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                        LabMessages.INTERNAL_SERVER_ERROR);
            }
        } catch (IOException writeFailure) {
            // The client went away mid-write; nothing more can be sent. Class name only, as above.
            log.warn("Could not write the error response for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), writeFailure.getClass().getName());
        }
        return new ModelAndView();
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.equals(API_PREFIX) || uri.startsWith(API_PREFIX + "/"));
    }
}
