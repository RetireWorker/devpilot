package com.devpilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Execution request sent through AgentExecutor. Workspace paths are relative
 * names resolved by the executor host; callers can never pass absolute paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecutionRequest(
        String executionKey,
        String taskId,
        String subtaskId,
        String projectId,
        String workspaceId,
        String workspaceRelativePath,
        AgentRole role,
        String objective,
        List<String> allowedPaths,
        List<String> forbiddenPaths,
        List<String> inputs,
        Map<String, String> metadata,
        Duration timeout,
        Map<String, String> resourceLimits) {
}
