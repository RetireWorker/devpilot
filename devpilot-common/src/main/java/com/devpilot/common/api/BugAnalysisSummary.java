package com.devpilot.common.api;

import java.util.List;

/**
 * Module-neutral view of a BUG analysis report. Task planning depends on this
 * record, not on the BUG module's persistence model.
 */
public record BugAnalysisSummary(
        String bugId,
        String title,
        String description,
        String expectedResult,
        String actualResult,
        String problemType,
        String severity,
        List<String> impactScope,
        List<String> suspectedModules,
        String errorLogSummary,
        List<String> missingInfo,
        String reproducibility) {
}
