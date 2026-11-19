package com.devpilot.common.api;

import java.util.List;

public record ProjectMatchView(
        String projectId,
        String projectName,
        double score,
        List<String> evidence) {
}
