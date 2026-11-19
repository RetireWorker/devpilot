package com.devpilot.task.service;

/**
 * Optional plan override. When subtasks are present the caller (or a human
 * approver) supplies the split; otherwise the plan port generates one.
 */
public record PlanRequest(
        String objective,
        java.util.List<com.devpilot.common.api.SubtaskSpec> subtasks,
        Boolean requiresHumanConfirmation) {
}
