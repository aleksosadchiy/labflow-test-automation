package com.labflow.common.model;

import java.util.Optional;

/**
 * The eight stages a sample moves through in the lab, in workflow order.
 * Declaration order <em>is</em> the workflow order, and {@link #next()} relies
 * on it. The constant names are the lab's JSON wire values, so they must be
 * neither reordered nor renamed; {@link #displayName()} is the human-readable
 * name the lab uses in its messages.
 */
public enum WorkflowStage {
    ACCESSIONED("Accessioned"),
    READY_FOR_PROCESSING("Ready for Processing"),
    PROCESSING("Processing"),
    SEQUENCING("Sequencing"),
    BIOINFORMATICS("Bioinformatics"),
    QUALITY_CHECK("Quality Check"),
    RESULT_READY("Result Ready"),
    REPORT_GENERATED("Report Generated");

    private final String displayName;

    WorkflowStage(String displayName) {
        this.displayName = displayName;
    }

    /** The stage name as users see it, e.g. "Ready for Processing". */
    public String displayName() {
        return displayName;
    }

    /** The stage that follows this one, or empty at {@link #REPORT_GENERATED}. */
    public Optional<WorkflowStage> next() {
        WorkflowStage[] stages = values();
        int following = ordinal() + 1;
        return following < stages.length ? Optional.of(stages[following]) : Optional.empty();
    }
}
