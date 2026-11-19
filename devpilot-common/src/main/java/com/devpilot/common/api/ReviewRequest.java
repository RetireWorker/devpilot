package com.devpilot.common.api;

import java.util.List;

public record ReviewRequest(
        String taskId,
        String projectId,
        String workspaceRelativePath,
        String baseRef,
        String commitSha,
        List<String> changedFiles,
        String objective) {
}
