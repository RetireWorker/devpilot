package com.devpilot.bug.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBugRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String description,
        String reproductionSteps,
        String expectedResult,
        String actualResult,
        String environment,
        String version,
        String timeRange,
        String errorLog,
        String severity,
        Boolean productionIssue,
        String specifiedProjectId) {
}
