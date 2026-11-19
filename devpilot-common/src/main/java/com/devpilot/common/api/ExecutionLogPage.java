package com.devpilot.common.api;

import java.time.Instant;
import java.util.List;

public record ExecutionLogPage(
        String executionId,
        List<LogLine> lines,
        String nextCursor,
        boolean truncated) {

    public record LogLine(Instant timestamp, String stream, String text) {
    }
}
