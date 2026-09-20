package com.labflow.client.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The one JSON mapper for everything that goes to or comes from the lab.
 *
 * <p>It is configured once and never changed afterwards, so sharing it between threads is
 * safe. Two settings matter:
 * <ul>
 *   <li>{@code JavaTimeModule}: the lab's ISO-8601 timestamps become {@code Instant}, and
 *       dates are written back as ISO-8601 text rather than numbers.</li>
 *   <li>Unknown properties are ignored: the lab may add fields (for example the nested
 *       {@code order} on a sample registration) without breaking the client. Missing
 *       fields or unknown enum names still fail loudly.</li>
 * </ul>
 * Records need no annotations: Jackson reads them by component name.
 */
public final class LabJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private LabJson() {
    }

    /** The shared mapper. Do not reconfigure it. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
