package com.devpilot.common.api;

import com.devpilot.common.domain.ExecutorMode;

import java.time.Instant;

public record ExecutorHealth(
        boolean healthy,
        String executorId,
        ExecutorMode mode,
        String detail,
        Instant checkedAt) {
}
