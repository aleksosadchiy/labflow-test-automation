package com.labflow.client.http;

// @layer: unit
// @spec: 003-test-automation-foundation

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.labflow.common.model.ApiError;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleSummary;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.StageEntry;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for {@link LabJson}, per functional-spec.md §2.4 (typed answers): the lab's JSON
 * turns into the shared records, timestamps become {@code Instant}, extra fields the lab
 * may add are ignored, and the wire enum names map to the enums. It covers the lab's order
 * and sample answers, including the nested {@code order} object the lab adds to a sample
 * registration and the bare JSON array of samples.
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

    // ---- the lab's order and sample answers ----

    private static final String SAMPLE_REGISTRATION_ANSWER = "{\"id\":\"s-1\",\"patientId\":\"p-1\","
            + "\"orderId\":\"o-1\",\"sampleType\":\"BLOOD\",\"status\":\"ACCESSIONED\","
            + "\"createdAt\":\"2026-01-05T10:15:30.123Z\","
            + "\"order\":{\"id\":\"o-1\",\"patientId\":\"p-1\",\"createdAt\":\"2026-01-05T10:15:00Z\"}}";

    @Test
    // @regression
    @DisplayName("The lab's sample-registration answer, with its nested order object, maps to a Sample")
    void sampleRegistrationAnswerWithNestedOrderMapsToASample() throws Exception {
        Sample sample = LabJson.mapper().readValue(SAMPLE_REGISTRATION_ANSWER, Sample.class);

        assertThat(sample).isEqualTo(new Sample("s-1", "p-1", "o-1", SampleType.BLOOD,
                WorkflowStage.ACCESSIONED, Instant.parse("2026-01-05T10:15:30.123Z")));
    }

    @Test
    // @regression
    @DisplayName("A sample's wire names (TISSUE, READY_FOR_PROCESSING) map to the enums")
    void sampleWireNamesMapToTheEnums() throws Exception {
        Sample sample = LabJson.mapper().readValue("{\"id\":\"s-2\",\"patientId\":\"p-1\",\"orderId\":\"o-1\","
                + "\"sampleType\":\"TISSUE\",\"status\":\"READY_FOR_PROCESSING\","
                + "\"createdAt\":\"2026-01-05T10:15:30Z\"}", Sample.class);

        assertThat(sample.sampleType()).isEqualTo(SampleType.TISSUE);
        assertThat(sample.status()).isEqualTo(WorkflowStage.READY_FOR_PROCESSING);
    }

    @Test
    // @regression
    @DisplayName("An order with a samples list maps to an Order whose samples are SampleSummary records")
    void orderWithSamplesMapsToAnOrder() throws Exception {
        String json = "{\"id\":\"o-1\",\"patientId\":\"p-1\",\"createdAt\":\"2026-01-05T10:15:00Z\","
                + "\"samples\":[{\"id\":\"s-1\",\"sampleType\":\"BLOOD\",\"status\":\"ACCESSIONED\"},"
                + "{\"id\":\"s-2\",\"sampleType\":\"SWAB\",\"status\":\"PROCESSING\"}]}";

        Order order = LabJson.mapper().readValue(json, Order.class);

        assertThat(order.id()).isEqualTo("o-1");
        assertThat(order.patientId()).isEqualTo("p-1");
        assertThat(order.createdAt()).isEqualTo(Instant.parse("2026-01-05T10:15:00Z"));
        assertThat(order.samples()).containsExactly(
                new SampleSummary("s-1", SampleType.BLOOD, WorkflowStage.ACCESSIONED),
                new SampleSummary("s-2", SampleType.SWAB, WorkflowStage.PROCESSING));
    }

    @Test
    // @regression
    @DisplayName("An order answer without a samples list, or with a null one, has an empty samples list")
    void orderWithoutSamplesHasAnEmptyList() throws Exception {
        Order absent = LabJson.mapper().readValue(
                "{\"id\":\"o-1\",\"patientId\":\"p-1\",\"createdAt\":\"2026-01-05T10:15:00Z\"}", Order.class);
        Order explicitNull = LabJson.mapper().readValue(
                "{\"id\":\"o-1\",\"patientId\":\"p-1\",\"createdAt\":\"2026-01-05T10:15:00Z\",\"samples\":null}",
                Order.class);

        assertThat(absent.samples()).isNotNull().isEmpty();
        assertThat(explicitNull.samples()).isNotNull().isEmpty();
    }

    @Test
    // @regression
    @DisplayName("An order's samples list cannot be modified")
    void orderSamplesAreImmutable() {
        Order order = new Order("o-1", "p-1", Instant.parse("2026-01-05T10:15:00Z"), null);

        assertThatThrownBy(() -> order.samples().add(new SampleSummary("s-1", SampleType.BLOOD, WorkflowStage.ACCESSIONED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    // @regression
    @DisplayName("A bare JSON array of samples maps to a List of Sample")
    void bareArrayOfSamplesMapsToAList() throws Exception {
        String json = "[" + SAMPLE_REGISTRATION_ANSWER + ",{\"id\":\"s-2\",\"patientId\":\"p-2\",\"orderId\":\"o-2\","
                + "\"sampleType\":\"URINE\",\"status\":\"SEQUENCING\",\"createdAt\":\"2026-01-05T11:00:00Z\"}]";

        List<Sample> samples = LabJson.mapper().readValue(json, new TypeReference<List<Sample>>() { });

        assertThat(samples).extracting(Sample::id, Sample::patientId, Sample::sampleType, Sample::status)
                .containsExactly(
                        tuple("s-1", "p-1", SampleType.BLOOD, WorkflowStage.ACCESSIONED),
                        tuple("s-2", "p-2", SampleType.URINE, WorkflowStage.SEQUENCING));
    }

    @Test
    // @regression
    @DisplayName("An empty JSON array maps to an empty List of Sample")
    void emptyArrayMapsToAnEmptyList() throws Exception {
        List<Sample> samples = LabJson.mapper().readValue("[]", new TypeReference<List<Sample>>() { });

        assertThat(samples).isEmpty();
    }

    // ---- progress, result and report ----

    @Test
    // @regression
    @DisplayName("A progress answer maps to a Progress whose stage list has typed stages and Instants, current stage included")
    void progressAnswerMapsToProgress() throws Exception {
        String json = "{\"sampleId\":\"s-1\",\"currentStatus\":\"READY_FOR_PROCESSING\","
                + "\"stages\":[{\"status\":\"ACCESSIONED\",\"reachedAt\":\"2026-01-05T10:15:30Z\"},"
                + "{\"status\":\"READY_FOR_PROCESSING\",\"reachedAt\":\"2026-01-05T10:16:00.500Z\"}]}";

        Progress progress = LabJson.mapper().readValue(json, Progress.class);

        assertThat(progress.sampleId()).isEqualTo("s-1");
        assertThat(progress.currentStatus()).isEqualTo(WorkflowStage.READY_FOR_PROCESSING);
        assertThat(progress.stages()).containsExactly(
                new StageEntry(WorkflowStage.ACCESSIONED, Instant.parse("2026-01-05T10:15:30Z")),
                new StageEntry(WorkflowStage.READY_FOR_PROCESSING, Instant.parse("2026-01-05T10:16:00.500Z")));
    }

    @Test
    // @regression
    @DisplayName("A progress answer without a stages list, or with a null one, has an empty stages list")
    void progressWithoutStagesHasAnEmptyList() throws Exception {
        Progress absent = LabJson.mapper().readValue(
                "{\"sampleId\":\"s-1\",\"currentStatus\":\"ACCESSIONED\"}", Progress.class);
        Progress explicitNull = LabJson.mapper().readValue(
                "{\"sampleId\":\"s-1\",\"currentStatus\":\"ACCESSIONED\",\"stages\":null}", Progress.class);

        assertThat(absent.stages()).isNotNull().isEmpty();
        assertThat(explicitNull.stages()).isNotNull().isEmpty();
    }

    @Test
    // @regression
    @DisplayName("A progress's stages list cannot be modified, and does not follow later changes to the list it was built from")
    void progressStagesAreImmutable() {
        ArrayList<StageEntry> source = new ArrayList<>();
        source.add(new StageEntry(WorkflowStage.ACCESSIONED, Instant.parse("2026-01-05T10:15:30Z")));
        Progress progress = new Progress("s-1", WorkflowStage.ACCESSIONED, source);
        source.clear();

        assertThat(progress.stages()).hasSize(1);
        assertThatThrownBy(() -> progress.stages().add(new StageEntry(WorkflowStage.PROCESSING, Instant.EPOCH)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    // @regression
    @DisplayName("A result answer maps to a TestResult, and fields the lab adds are ignored")
    void resultAnswerMapsToATestResult() throws Exception {
        TestResult result = LabJson.mapper().readValue(
                "{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\",\"resultValue\":\"13.5 g/dL\",\"extra\":{\"a\":1}}",
                TestResult.class);

        assertThat(result).isEqualTo(new TestResult("s-1", "Hemoglobin", "13.5 g/dL"));
    }

    @Test
    // @regression
    @DisplayName("A report answer maps to a Report with generatedAt as an Instant, and fields the lab adds are ignored")
    void reportAnswerMapsToAReport() throws Exception {
        Report report = LabJson.mapper().readValue("{\"sampleId\":\"s-1\",\"testName\":\"Hemoglobin\","
                + "\"resultValue\":\"13.5 g/dL\",\"generatedAt\":\"2026-01-05T11:00:00.750Z\",\"extra\":42}", Report.class);

        assertThat(report).isEqualTo(new Report("s-1", "Hemoglobin", "13.5 g/dL",
                Instant.parse("2026-01-05T11:00:00.750Z")));
    }

    @Test
    // @regression
    @DisplayName("A progress answer with a stage name the framework does not know fails loudly")
    void progressWithAnUnknownStageFailsLoudly() {
        assertThatThrownBy(() -> LabJson.mapper().readValue(
                "{\"sampleId\":\"s-1\",\"currentStatus\":\"TELEPORTED\",\"stages\":[]}", Progress.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("TELEPORTED");
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
