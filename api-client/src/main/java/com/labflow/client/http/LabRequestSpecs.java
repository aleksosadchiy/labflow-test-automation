package com.labflow.client.http;

import com.labflow.common.config.ApiKey;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.io.PrintStream;

/**
 * Builds the RestAssured request specification every call to the lab starts from.
 *
 * <p>One fresh specification is built <em>per request</em> and nothing is written to
 * RestAssured's static defaults ({@code RestAssured.baseURI}, {@code RestAssured.config}
 * and so on). Static defaults are global mutable state shared by every thread, which
 * would make parallel execution unsafe later.
 *
 * <p>Every specification has: the lab's base address, JSON in and out, the shared
 * {@link LabJson} mapper, connect and read timeouts, and a log configuration that
 * blacklists the {@code Authorization} header. RestAssured logging itself stays off
 * unless a caller asks for it with {@code .log()}.
 *
 * <p>Every specification also carries a {@link RequestLogFilter}, so each request through
 * the client writes one line (no headers, no names) to the run log. Its body setting
 * ({@code -DlogBodies} / {@code LOG_BODIES}) is read each time a specification is built.
 */
public final class LabRequestSpecs {

    /** How long to wait for the connection to the lab to open. */
    static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    /** How long to wait for the lab to answer once connected. */
    static final int READ_TIMEOUT_MILLIS = 10_000;

    /** Apache HttpClient parameter names used by RestAssured 5. */
    static final String CONNECT_TIMEOUT_PARAM = "http.connection.timeout";
    static final String READ_TIMEOUT_PARAM = "http.socket.timeout";

    private LabRequestSpecs() {
    }

    /**
     * @param baseUrl the lab's address without a trailing slash
     * @param key     the access key to send as {@code Authorization: Bearer <key>}, or
     *                {@code null} to send no {@code Authorization} header at all
     */
    public static RequestSpecification create(String baseUrl, ApiKey key) {
        return create(baseUrl, key, logConfig());
    }

    /**
     * Same as {@link #create(String, ApiKey)}, but anything RestAssured logs for this
     * request goes to {@code logStream} instead of the console.
     */
    public static RequestSpecification create(String baseUrl, ApiKey key, PrintStream logStream) {
        return create(baseUrl, key, logConfig().defaultStream(logStream));
    }

    private static LogConfig logConfig() {
        return LogConfig.logConfig().blacklistHeader("Authorization");
    }

    private static RequestSpecification create(String baseUrl, ApiKey key, LogConfig logConfig) {
        RestAssuredConfig config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .jackson2ObjectMapperFactory((type, charset) -> LabJson.mapper()))
                .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam(CONNECT_TIMEOUT_PARAM, CONNECT_TIMEOUT_MILLIS)
                        .setParam(READ_TIMEOUT_PARAM, READ_TIMEOUT_MILLIS))
                .logConfig(logConfig);

        RequestSpecification spec = RestAssured.with()
                .baseUri(baseUrl)
                .config(config)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .filter(RequestLogFilter.fromEnvironment());
        if (key != null) {
            // The one place the raw key is read: it goes straight into the header.
            spec.header("Authorization", "Bearer " + key.reveal());
        }
        return spec;
    }
}
