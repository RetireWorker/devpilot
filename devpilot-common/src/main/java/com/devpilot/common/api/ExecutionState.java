package com.devpilot.common.api;

import java.time.Instant;

public record ExecutionState(
        String executionId,
        ExecutionStatus status,
        int exitCode,
        Instant startedAt,
        Instant finishedAt,
        String message) {
}
