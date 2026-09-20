package com.mocklab.api.error;

import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only stand-in for the real API controllers (which arrive in later
 * slices), so the error mapping can be proven before any real route exists.
 *
 * <p>It is a {@code @TestComponent}, which Spring Boot's component scan
 * skips, so it never appears in another test class's application context. Only
 * {@link ApiErrorMappingIntegrationTest} pulls it in, with an explicit
 * {@code @Import}.
 */
@TestComponent
@RestController
@RequestMapping("/api/test-support")
public class ApiErrorTestSupportController {

    /** Text that must never be echoed back to a client or written to the log. */
    public static final String SECRET_TEXT = "Jane Doe MRN-424242";

    /** Throws a business-rule failure of the requested kind. */
    @GetMapping("/fail/{kind}")
    public String fail(@PathVariable FailureKind kind) {
        String message = switch (kind) {
            case VALIDATION -> LabMessages.PATIENT_NAME_REQUIRED;
            case NOT_FOUND -> LabMessages.SAMPLE_NOT_FOUND;
            case CONFLICT -> LabMessages.DUPLICATE_SAMPLE_TYPE;
        };
        throw new LabRuleException(kind, message);
    }

    /** Accepts a JSON body, so a broken or wrongly shaped body is unreadable. */
    @PostMapping(value = "/echo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> echo(@RequestBody EchoRequest request) {
        return Map.of("name", String.valueOf(request.name()));
    }

    /** Supports GET only, so any other verb is a 405. */
    @GetMapping("/get-only")
    public Map<String, String> getOnly() {
        return Map.of("ok", "true");
    }

    /** Can only answer JSON, so {@code Accept: application/xml} is a 406. */
    @GetMapping(value = "/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> jsonOnly() {
        return Map.of("ok", "true");
    }

    /** An unexpected failure whose message carries "patient data". */
    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("Lookup failed for " + SECRET_TEXT);
    }

    public record EchoRequest(String name, Integer count) {
    }
}
