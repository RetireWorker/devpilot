package com.devpilot.common.api;

import java.time.Instant;
import java.util.List;

public record TestRunResult(
        boolean passed,
        int exitCode,
        String command,
        List<String> failureSummary,
        String reportLocation,
        Instant finishedAt,
        long durationMillis) {
}
