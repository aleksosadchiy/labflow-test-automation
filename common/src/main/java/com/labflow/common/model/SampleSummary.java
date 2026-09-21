package com.labflow.common.model;

/**
 * One sample as it appears inside an {@link Order} the lab answers.
 *
 * @param id         the identifier the lab assigned to the sample
 * @param sampleType the kind of sample
 * @param status     the workflow stage the sample is at
 */
public record SampleSummary(String id, SampleType sampleType, WorkflowStage status) {
}
