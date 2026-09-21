package com.labflow.common.model;

import java.util.List;

/**
 * How far a sample has come, as the lab answers {@code GET /api/samples/{id}/progress}. It is
 * the "processing job" of a sample. Note the lab names the current stage
 * {@code currentStatus} here but {@code status} on a {@link Sample}.
 *
 * @param sampleId      the sample this progress belongs to
 * @param currentStatus the stage the sample is at now
 * @param stages        every stage reached so far, the current one included; never
 *                      {@code null} (an answer without the list reads as an empty one) and
 *                      never modifiable
 */
public record Progress(String sampleId, WorkflowStage currentStatus, List<StageEntry> stages) {

    public Progress {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
