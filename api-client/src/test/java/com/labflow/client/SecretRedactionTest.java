package com.labflow.client;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.support.StubLab;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the access key cannot leak from the client layer, per functional-spec.md §2.3
 * (the key never appears in any output) and technical-considerations.md §2.5 (four layers
 * of protection). The one place RestAssured can print a request is {@code .log().all()};
 * here it is pointed at a captured stream and the key is looked for in it. The distinctive
 * key below must also stay out of every printed form of the client and its configuration.
 *
 * @spec: 003-test-automation-foundation
 */
class SecretRedactionTest {

    /** A distinctive value that must never appear in any output. */
    private static final String SECRET = "s3cret-client-key-7731";

    private StubLab lab;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final PrintStream logStream = new PrintStream(captured, true, StandardCharsets.UTF_8);

    @BeforeEach
    void startLab() {
        lab = StubLab.start().answer("GET", "/api/patients/p-1", 200, "{\"id\":\"p-1\",\"name\":\"Ada\"}");
    }

    @AfterEach
    void stopLab() {
        lab.close();
    }

    private String logged() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    // @regression
    @DisplayName("A request logged with log().all() shows Authorization as blacklisted and never the key")
    void loggedRequestBlacklistsTheAuthorizationHeader() {
        RestAssured.given()
                .spec(LabRequestSpecs.create(lab.baseUrl(), new ApiKey(SECRET), logStream))
                .log().all()
                .get("/api/patients/p-1");

        // The header really was sent (so hiding it in the log is meaningful) ...
        assertThat(lab.lastRequest().header("Authorization")).contains("Bearer " + SECRET);
        // ... and the log shows that it was sent, but not what it contained.
        assertThat(logged()).contains("Authorization").contains("BLACKLISTED");
        assertThat(logged()).doesNotContain(SECRET).doesNotContain("Bearer s3cret");
    }

    @Test
    // @regression
    @DisplayName("A response logged with log().all() does not contain the key either")
    void loggedResponseDoesNotContainTheKey() {
        RestAssured.given()
                .spec(LabRequestSpecs.create(lab.baseUrl(), new ApiKey(SECRET), logStream))
                .get("/api/patients/p-1")
                .then().log().all();

        assertThat(logged()).isNotEmpty().doesNotContain(SECRET);
    }

    @Test
    // @regression
    @DisplayName("Nothing is logged unless a caller asks for it")
    void loggingIsOffByDefault() {
        RestAssured.given()
                .spec(LabRequestSpecs.create(lab.baseUrl(), new ApiKey(SECRET), logStream))
                .get("/api/patients/p-1");

        assertThat(logged()).isEmpty();
    }

    @Test
    // @regression
    @DisplayName("The client, its configuration and the key print as *** and never as the key")
    void printedFormsNeverContainTheKey() {
        LabConfig config = new LabConfig(lab.baseUrl(), new ApiKey(SECRET));
        LabClient client = LabClient.create(config);

        assertThat(new ApiKey(SECRET).toString()).isEqualTo("***");
        assertThat(config.toString()).doesNotContain(SECRET).contains("***");
        assertThat(client.toString()).doesNotContain(SECRET).contains("***");
        assertThat(client.withApiKey(SECRET).toString()).doesNotContain(SECRET).contains("***");
        assertThat(client.patients().toString()).doesNotContain(SECRET);
    }

    @Test
    // @regression
    @DisplayName("A client without a key says so, and there is no key to show")
    void clientWithoutKeyPrintsNoKey() {
        LabClient client = LabClient.create(new LabConfig(lab.baseUrl(), new ApiKey(SECRET)));

        assertThat(client.withoutApiKey().toString()).doesNotContain(SECRET).doesNotContain("***").contains("none");
    }
}
