package com.devpilot.common.api;

import java.time.Instant;

public record ExecutionSubmitResult(
        String executionId,
        String executionKey,
        ExecutionStatus status,
        Instant acceptedAt,
        boolean idempotentReplay) {
}
