package com.devpilot.common.api;

import java.time.Duration;
import java.util.Map;

public record TestExecutionRequest(
        String taskId,
        String projectId,
        String workspaceRelativePath,
        String commandKey,
        Map<String, String> parameters,
        Duration timeout) {
}
