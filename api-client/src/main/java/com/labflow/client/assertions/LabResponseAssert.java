package com.labflow.client.assertions;

import com.labflow.client.http.LabResponse;
import com.labflow.client.http.Rejection;
import org.assertj.core.api.AbstractAssert;

import java.util.Objects;
import java.util.Optional;

/**
 * Assertions that read as business statements about one answer from the lab: it was refused
 * as a given kind with the lab's exact message, and it did or did not carry certain headers.
 *
 * <p>Every failure starts with the request and what the lab answered, in square brackets
 * ({@link LabResponse#describe()}), and includes the lab's own message, so a failure explains
 * itself without a debugger. A failure never prints any header other than the one being
 * asserted, never a request body, and never the value of an {@code Authorization} header
 * (for that header it says only whether it was present).
 */
public final class LabResponseAssert extends AbstractAssert<LabResponseAssert, LabResponse<?>> {

    private static final String AUTHORIZATION = "Authorization";

    LabResponseAssert(LabResponse<?> actual) {
        super(actual, LabResponseAssert.class);
    }

    /**
     * The lab refused the request as {@code kind} (that kind's status) <em>and</em> said
     * exactly {@code message}. A success answer, another kind of refusal, and the right kind
     * with different wording all fail.
     */
    public LabResponseAssert isRejectedAs(Rejection kind, String message) {
        isNotNull();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        String expected = "expected rejection " + kind + " \"" + message + "\"";

        if (actual.statusCode() != kind.status()) {
            String answered = "but the lab answered " + actual.statusCode();
            String labMessage = actual.message();
            fail(expected + " " + answered + (labMessage.isEmpty() ? "" : " \"" + labMessage + "\""));
        }
        if (!message.equals(actual.message())) {
            String labMessage = actual.message();
            fail(expected + " but the lab " + (labMessage.isEmpty() ? "gave no message" : "said \"" + labMessage + "\""));
        }
        return this;
    }

    /** The answer has a header of that name (any capitalisation) whose first value is exactly {@code value}. */
    public LabResponseAssert hasHeader(String name, String value) {
        isNotNull();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Optional<String> header = actual.header(name);
        boolean secret = AUTHORIZATION.equalsIgnoreCase(name);
        if (header.isEmpty()) {
            fail("expected header \"" + name + "\"" + (secret ? "" : " to be \"" + value + "\"")
                    + " but the lab sent no such header");
        }
        if (!value.equals(header.get())) {
            fail("expected header \"" + name + "\"" + (secret ? "" : " to be \"" + value + "\"")
                    + " but " + (secret ? "its value differs (not shown)" : "it was \"" + header.get() + "\""));
        }
        return this;
    }

    /** The answer has a header of that name (any capitalisation) whose first value contains {@code fragment}. */
    public LabResponseAssert hasHeaderContaining(String name, String fragment) {
        isNotNull();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fragment, "fragment");
        Optional<String> header = actual.header(name);
        boolean secret = AUTHORIZATION.equalsIgnoreCase(name);
        if (header.isEmpty()) {
            fail("expected header \"" + name + "\"" + (secret ? "" : " to contain \"" + fragment + "\"")
                    + " but the lab sent no such header");
        }
        if (!header.get().contains(fragment)) {
            fail("expected header \"" + name + "\"" + (secret ? "" : " to contain \"" + fragment + "\"")
                    + " but " + (secret ? "its value does not (not shown)" : "it was \"" + header.get() + "\""));
        }
        return this;
    }

    /** The answer carries no {@code Location} header, so nothing was reported as created. */
    public LabResponseAssert hasNoLocationHeader() {
        isNotNull();
        Optional<String> location = actual.header("Location");
        if (location.isPresent()) {
            fail("expected no Location header but the lab sent \"" + location.get() + "\"");
        }
        return this;
    }

    /** Fails with the request line first, then what was expected. Never returns. */
    private void fail(String what) {
        // "%s" keeps a '%' in the lab's own text from being read as a format instruction.
        failWithMessage("%s", "[" + actual.describe() + "] " + what);
    }
}
