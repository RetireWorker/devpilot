package com.devpilot.common.api;

import java.util.List;

public record TaskPlanView(
        String taskId,
        String projectId,
        String objective,
        List<SubtaskSpec> subtasks,
        boolean requiresHumanConfirmation) {
}
