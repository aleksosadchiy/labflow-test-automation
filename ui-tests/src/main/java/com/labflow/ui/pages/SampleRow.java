package com.labflow.ui.pages;

import com.labflow.common.model.WorkflowStage;

/**
 * One row of the Sample List screen, as read from the screen.
 *
 * @param sampleId    the sample's id, as shown in the first column
 * @param patientName the patient's name, as shown in the second column
 * @param status      the sample's current stage, mapped from the third column's text
 */
public record SampleRow(String sampleId, String patientName, WorkflowStage status) {
}
