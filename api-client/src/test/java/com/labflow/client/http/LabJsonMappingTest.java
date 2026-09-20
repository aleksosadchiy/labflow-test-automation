package com.labflow.client.http;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.JsonProcessingException;
import com.labflow.common.model.ApiError;
import com.labflow.common.model.Patient;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LabJson}, per functional-spec.md §2.4 (typed answers): the lab's JSON
 * turns into the shared records, timestamps become {@code Instant}, extra fields the lab
 * may add are ignored, and the wire enum names map to the enums. A small test-local
 * record stands in for the lab's larger answers that do not exist yet.
 *
 * @spec: 003-test-automation-foundation
 */
class LabJsonMappingTest {

    /** Stands in for a lab answer that carries a timestamp. */
    record Stamped(Instant at) {
    }

    /** Stands in for a lab answer that carries both wire enums. */
    record Wire(WorkflowStage status, SampleType sampleType) {
    }

    // ---- Instant ----

    @Test
    // @regression
    @DisplayName("An ISO-8601 timestamp from the lab becomes an Instant")
    void isoTimestampBecomesAnInstant() throws Exception {
        Stamped stamped = LabJson.mapper().readValue("{\"at\":\"2026-01-05T10:15:30.123Z\"}", Stamped.class);

        assertThat(stamped.at()).isEqualTo(Instant.parse("2026-01-05T10:15:30.123Z"));
    }

    @Test
    // @regression
    @DisplayName("An Instant is written back as ISO-8601 text, not as a number")
    void instantIsWrittenAsIsoText() throws Exception {
        String json = LabJson.mapper().writeValueAsString(new Stamped(Instant.parse("2026-01-05T10:15:30Z")));

        assertThat(json).isEqualTo("{\"at\":\"2026-01-05T10:15:30Z\"}");
    }

    @Test
    // @regression
    @DisplayName("A timestamp that is not ISO-8601 is refused rather than guessed")
    void nonIsoTimestampIsRefused() {
        assertThatThrownBy(() -> LabJson.mapper().readValue("{\"at\":\"yesterday\"}", Stamped.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    // ---- unknown fields ----

    @Test
    // @regression
    @DisplayName("Fields the lab adds that the record does not know (a nested object, a scalar) are ignored")
    void unknownFieldsAreIgnored() throws Exception {
        String json = "{\"id\":\"p-1\",\"name\":\"Ada\","
                + "\"order\":{\"id\":\"o-1\",\"samples\":[]},\"extra\":42}";

        Patient patient = LabJson.mapper().readValue(json, Patient.class);

        assertThat(patient).isEqualTo(new Patient("p-1", "Ada"));
    }

    // ---- enum wire names ----

    @Test
    // @regression
    @DisplayName("The lab's wire names READY_FOR_PROCESSING and BLOOD map to the enums")
    void wireNamesMapToTheEnums() throws Exception {
        Wire wire = LabJson.mapper().readValue(
                "{\"status\":\"READY_FOR_PROCESSING\",\"sampleType\":\"BLOOD\"}", Wire.class);

        assertThat(wire.status()).isEqualTo(WorkflowStage.READY_FOR_PROCESSING);
        assertThat(wire.sampleType()).isEqualTo(SampleType.BLOOD);
    }

    @ParameterizedTest
    // @regression
    @EnumSource(WorkflowStage.class)
    @DisplayName("Every workflow stage's constant name is its wire name")
    void everyStageIsReadableByItsName(WorkflowStage stage) throws Exception {
        Wire wire = LabJson.mapper().readValue(
                "{\"status\":\"" + stage.name() + "\",\"sampleType\":\"BLOOD\"}", Wire.class);

        assertThat(wire.status()).isEqualTo(stage);
    }

    @ParameterizedTest
    // @regression
    @EnumSource(SampleType.class)
    @DisplayName("Every sample type's constant name is its wire name")
    void everySampleTypeIsReadableByItsName(SampleType type) throws Exception {
        Wire wire = LabJson.mapper().readValue(
                "{\"status\":\"ACCESSIONED\",\"sampleType\":\"" + type.name() + "\"}", Wire.class);

        assertThat(wire.sampleType()).isEqualTo(type);
    }

    @Test
    // @regression
    @DisplayName("A stage or sample type name the framework does not know fails loudly")
    void unknownEnumNameFailsLoudly() {
        assertThatThrownBy(() -> LabJson.mapper().readValue(
                "{\"status\":\"TELEPORTED\",\"sampleType\":\"BLOOD\"}", Wire.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("TELEPORTED");
        assertThatThrownBy(() -> LabJson.mapper().readValue(
                "{\"status\":\"ACCESSIONED\",\"sampleType\":\"PLASMA\"}", Wire.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("PLASMA");
    }

    // ---- the lab's error body ----

    @Test
    // @regression
    @DisplayName("The lab's error body maps to ApiError: status, error, message, path, timestamp")
    void labErrorBodyMapsToApiError() throws Exception {
        String json = "{\"status\":409,\"error\":\"Conflict\","
                + "\"message\":\"A sample of this type already exists for this order.\","
                + "\"path\":\"/api/samples\",\"timestamp\":\"2026-01-05T10:15:30.123Z\"}";

        ApiError error = LabJson.mapper().readValue(json, ApiError.class);

        assertThat(error).isEqualTo(new ApiError(409, "Conflict",
                "A sample of this type already exists for this order.", "/api/samples",
                Instant.parse("2026-01-05T10:15:30.123Z")));
    }
}
