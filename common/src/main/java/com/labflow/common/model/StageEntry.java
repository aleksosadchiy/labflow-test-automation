package com.labflow.common.model;

import java.time.Instant;

/**
 * One line of a sample's {@link Progress}: a stage the sample has reached and when.
 *
 * @param status    the workflow stage
 * @param reachedAt when the sample reached that stage
 */
public record StageEntry(WorkflowStage status, Instant reachedAt) {
}
