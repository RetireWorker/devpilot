package com.devpilot.common.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record AgentExecutionResult(
        String executionId,
        ExecutionStatus status,
        String summary,
        List<String> changedFiles,
        String commitSha,
        Map<String, Object> structuredOutput,
        List<String> violations,
        Instant finishedAt) {
}
