package com.mocklab.domain;

// @layer: unit
// @spec: 002-mock-lab-rest-api

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SampleStatus#displayName()} and
 * {@link SampleStatus#parse(String)}, per functional-spec.md §2.5 (stage names
 * as users see them) and technical-considerations.md §2.2/§2.3 (wire values
 * are enum names, messages use display names). No Spring context.
 *
 * @spec: 002-mock-lab-rest-api
 */
class SampleStatusTest {

    @Test
    void declarationOrderIsTheWorkflowOrder() {
        assertThat(Arrays.stream(SampleStatus.values()).map(SampleStatus::name))
                .containsExactly(
                        "ACCESSIONED",
                        "READY_FOR_PROCESSING",
                        "PROCESSING",
                        "SEQUENCING",
                        "BIOINFORMATICS",
                        "QUALITY_CHECK",
                        "RESULT_READY",
                        "REPORT_GENERATED");
    }

    @Test
    void displayNameIsTheHumanReadableStageNameForEveryStatus() {
        assertThat(SampleStatus.ACCESSIONED.displayName()).isEqualTo("Accessioned");
        assertThat(SampleStatus.READY_FOR_PROCESSING.displayName()).isEqualTo("Ready for Processing");
        assertThat(SampleStatus.PROCESSING.displayName()).isEqualTo("Processing");
        assertThat(SampleStatus.SEQUENCING.displayName()).isEqualTo("Sequencing");
        assertThat(SampleStatus.BIOINFORMATICS.displayName()).isEqualTo("Bioinformatics");
        assertThat(SampleStatus.QUALITY_CHECK.displayName()).isEqualTo("Quality Check");
        assertThat(SampleStatus.RESULT_READY.displayName()).isEqualTo("Result Ready");
        assertThat(SampleStatus.REPORT_GENERATED.displayName()).isEqualTo("Report Generated");
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseResolvesTheEnumName(SampleStatus status) {
        assertThat(SampleStatus.parse(status.name())).contains(status);
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseIsCaseInsensitiveOnTheEnumName(SampleStatus status) {
        assertThat(SampleStatus.parse(status.name().toLowerCase())).contains(status);
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseResolvesTheDisplayName(SampleStatus status) {
        assertThat(SampleStatus.parse(status.displayName())).contains(status);
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseResolvesTheUpperCasedDisplayName(SampleStatus status) {
        assertThat(SampleStatus.parse(status.displayName().toUpperCase())).contains(status);
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseIgnoresSurroundingWhitespace(SampleStatus status) {
        assertThat(SampleStatus.parse("  " + status.displayName() + "\t ")).contains(status);
        assertThat(SampleStatus.parse(" " + status.name() + " ")).contains(status);
    }

    @ParameterizedTest
    @EnumSource(SampleStatus.class)
    void parseTreatsRepeatedSpacesBetweenWordsAsOneSeparator(SampleStatus status) {
        assertThat(SampleStatus.parse(status.displayName().replace(" ", "   "))).contains(status);
    }

    @Test
    void parseResolvesMixedFormsOfAMultiWordStage() {
        assertThat(SampleStatus.parse("Ready for Processing")).contains(SampleStatus.READY_FOR_PROCESSING);
        assertThat(SampleStatus.parse("READY_FOR_PROCESSING")).contains(SampleStatus.READY_FOR_PROCESSING);
        assertThat(SampleStatus.parse("ready_for_processing")).contains(SampleStatus.READY_FOR_PROCESSING);
        assertThat(SampleStatus.parse("  ready   for  processing ")).contains(SampleStatus.READY_FOR_PROCESSING);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "NOPE",
            "PROCESS",
            "READY-FOR-PROCESSING",
            "Ready_For Processing extra"
    })
    void parseReturnsEmptyForNullBlankOrUnknownInput(String input) {
        Optional<SampleStatus> parsed = SampleStatus.parse(input);

        assertThat(parsed).isEmpty();
    }
}
