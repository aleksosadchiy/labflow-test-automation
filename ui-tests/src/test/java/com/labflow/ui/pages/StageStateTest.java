package com.labflow.ui.pages;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StageState} (tech spec §2.3): each {@code stage-*} CSS class the Details
 * screen puts on a stage maps to its state, surrounding whitespace is ignored, and an unknown
 * class fails loudly.
 *
 * @spec: 004-ui-automation-page-objects
 */
class StageStateTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "stage-completed, COMPLETED",
            "stage-current,   CURRENT",
            "stage-remaining, REMAINING"
    })
    // @regression
    @DisplayName("Each stage class the Details screen uses maps to its state")
    void eachStageClassMapsToItsState(String cssClass, StageState expected) {
        assertThat(StageState.fromCssClass(cssClass)).isEqualTo(expected);
    }

    @Test
    // @regression
    @DisplayName("Whitespace around the class is ignored")
    void surroundingWhitespaceIsIgnored() {
        assertThat(StageState.fromCssClass("  stage-current\n")).isEqualTo(StageState.CURRENT);
    }

    @Test
    // @regression
    @DisplayName("An unknown class fails loudly, naming the class and the known ones")
    void unknownClassFailsLoudly() {
        assertThatThrownBy(() -> StageState.fromCssClass("stage-skipped"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'stage-skipped'")
                .hasMessageContaining("stage-remaining");
    }

    @Test
    // @regression
    @DisplayName("A missing class fails loudly instead of with a NullPointerException")
    void missingClassFailsLoudly() {
        assertThatThrownBy(() -> StageState.fromCssClass(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("''");
    }
}
