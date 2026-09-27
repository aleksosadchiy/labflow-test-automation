package com.labflow.ui.pages;

import com.labflow.common.model.WorkflowStage;
import java.util.Arrays;

/**
 * The one place where a stage's text on a screen becomes a {@link WorkflowStage}. The lab's
 * screens show the enum constant name (for example {@code ACCESSIONED}), not the display name,
 * so the mapping is {@link WorkflowStage#valueOf}. Text the framework does not know fails
 * loudly with the raw value, so a changed screen is obvious rather than silently misread.
 */
final class StatusText {

    private StatusText() {
    }

    /** Maps the stage text shown on a screen (surrounding whitespace ignored) to its stage. */
    static WorkflowStage toStage(String shown) {
        String text = shown == null ? "" : shown.strip();
        try {
            return WorkflowStage.valueOf(text);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Unrecognised stage text on screen: '" + text
                    + "'. Expected one of " + Arrays.toString(WorkflowStage.values()), unknown);
        }
    }
}
