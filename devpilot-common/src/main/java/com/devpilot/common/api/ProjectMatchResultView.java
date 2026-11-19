package com.devpilot.common.api;

import java.util.List;

public record ProjectMatchResultView(
        String projectId,
        double confidence,
        MatchDecision decision,
        List<String> signals,
        List<ProjectMatchView> candidates) {

    public enum MatchDecision {
        AUTO_MATCHED,
        NEEDS_CONFIRMATION,
        UNMATCHED
    }
}
