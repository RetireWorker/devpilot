package com.devpilot.bug.api;

import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectMatchResultView;

import java.time.Instant;

public record BugView(
        String id,
        String title,
        String description,
        String severity,
        boolean productionIssue,
        String specifiedProjectId,
        String status,
        Instant createdAt,
        BugAnalysisSummary analysis,
        ProjectMatchResultView match) {
}
