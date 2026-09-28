package com.labflow.ui.pages;

import com.labflow.common.model.WorkflowStage;

/**
 * One stage in the Details screen's stage list, as read from the screen.
 *
 * @param stage the stage, mapped from the item's text
 * @param state whether the stage is completed, current or not yet reached, from its CSS class
 */
public record StageView(WorkflowStage stage, StageState state) {
}
