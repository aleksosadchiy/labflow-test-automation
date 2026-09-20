package com.labflow.common.model;

import java.time.Instant;

/**
 * The body the lab sends with every refusal, whatever the kind of refusal.
 *
 * @param status    the HTTP status, repeated in the body
 * @param error     the HTTP reason phrase (for example {@code Conflict})
 * @param message   the lab's own explanation; checks assert on this exact text
 * @param path      the request path the lab refused
 * @param timestamp when the lab refused it
 */
public record ApiError(int status, String error, String message, String path, Instant timestamp) {
}
