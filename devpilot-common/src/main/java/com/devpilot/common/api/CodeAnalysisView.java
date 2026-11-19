package com.devpilot.common.api;

import java.util.List;

public record CodeAnalysisView(
        String taskId,
        String projectId,
        List<String> relevantModules,
        List<String> relevantFiles,
        List<String> suspiciousChanges,
        List<String> evidence,
        String calledChainSummary) {
}
