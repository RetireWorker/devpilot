package com.devpilot.task.service;

import com.devpilot.common.domain.TaskEvent;
import com.devpilot.common.domain.TaskState;

import java.time.Instant;
import java.util.List;

public record TaskStatusView(
        String taskId,
        String bugId,
        String projectId,
        TaskState state,
        int repairRound,
        int maxRepairRounds,
        double matchedConfidence,
        String workspaceId,
        String workspaceRelativePath,
        String branch,
        List<TaskEvent> allowedEvents,
        Instant updatedAt) {
}
