package com.devpilot.task.service;

import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.domain.TaskState;

import java.time.Instant;
import java.util.List;

public record TaskReportView(
        String taskId,
        String bugId,
        String projectId,
        String title,
        String objective,
        TaskState state,
        int repairRound,
        int maxRepairRounds,
        double matchedConfidence,
        String workspaceId,
        String branch,
        List<SubtaskView> subtasks,
        CodeChangeView codeChange,
        List<TestView> tests,
        List<ReviewView> reviews,
        List<ApprovalView> approvals,
        List<EventView> events,
        List<String> nextActions) {

    public record SubtaskView(String id, String role, String type, String status,
                              List<String> dependsOn, List<String> allowedPaths, String acceptance) {
    }

    public record TestView(int round, boolean passed, Integer exitCode, String summary, Instant createdAt) {
    }

    public record ReviewView(int round, boolean passed, List<String> blocking,
                             List<String> warnings, List<String> risks, String summary, Instant createdAt) {
    }

    public record ApprovalView(String type, String decision, String approver, String comment, Instant createdAt) {
    }

    public record EventView(String event, String fromState, String toState,
                            String actor, String detail, Instant createdAt) {
    }
}
