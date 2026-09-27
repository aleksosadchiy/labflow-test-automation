package com.labflow.ui.pages;

import java.util.Arrays;

/**
 * Where one stage stands relative to the sample's current stage, as the Details screen marks it
 * with the stage's {@code stage-*} CSS class.
 */
public enum StageState {
    /** Already passed: the stage comes before the current one ({@code stage-completed}). */
    COMPLETED("stage-completed"),
    /** The sample's current stage ({@code stage-current}). */
    CURRENT("stage-current"),
    /** Not yet reached: the stage comes after the current one ({@code stage-remaining}). */
    REMAINING("stage-remaining");

    private final String cssClass;

    StageState(String cssClass) {
        this.cssClass = cssClass;
    }

    /**
     * Maps a stage item's {@code class} attribute (surrounding whitespace ignored) to its state.
     * A class the framework does not know fails loudly with the raw value, so a changed screen is
     * obvious rather than silently misread.
     */
    static StageState fromCssClass(String shown) {
        String text = shown == null ? "" : shown.strip();
        return Arrays.stream(values())
                .filter(state -> state.cssClass.equals(text))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unrecognised stage class on screen: '" + text
                        + "'. Expected one of stage-completed, stage-current, stage-remaining"));
    }
}
