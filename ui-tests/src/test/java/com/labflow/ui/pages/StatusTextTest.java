package com.labflow.ui.pages;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import com.labflow.common.model.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StatusText} (tech spec §2.3): the stage text the lab's screens show is
 * the enum constant name, surrounding whitespace is ignored, and unknown text fails loudly.
 *
 * @spec: 004-ui-automation-page-objects
 */
class StatusTextTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(WorkflowStage.class)
    // @regression
    @DisplayName("Every stage's constant name, as the screens show it, maps to that stage")
    void everyStageNameMapsToItsStage(WorkflowStage stage) {
        assertThat(StatusText.toStage(stage.name())).isEqualTo(stage);
    }

    @Test
    // @regression
    @DisplayName("Whitespace around the shown text is ignored")
    void surroundingWhitespaceIsIgnored() {
        assertThat(StatusText.toStage("\n   ACCESSIONED \t")).isEqualTo(WorkflowStage.ACCESSIONED);
    }

    @Test
    // @regression
    @DisplayName("Unknown text fails loudly, naming the text and the known stages")
    void unknownTextFailsLoudly() {
        assertThatThrownBy(() -> StatusText.toStage("Accessioned"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'Accessioned'")
                .hasMessageContaining("REPORT_GENERATED");
    }

    @Test
    // @regression
    @DisplayName("Missing text fails loudly instead of with a NullPointerException")
    void missingTextFailsLoudly() {
        assertThatThrownBy(() -> StatusText.toStage(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("''");
    }
}
