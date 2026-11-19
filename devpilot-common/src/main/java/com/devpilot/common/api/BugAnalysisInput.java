package com.devpilot.common.api;

public record BugAnalysisInput(
        String bugId,
        String title,
        String description,
        String reproductionSteps,
        String expectedResult,
        String actualResult,
        String environment,
        String version,
        String errorLog,
        String severity,
        boolean productionIssue,
        String specifiedProjectId) {
}
