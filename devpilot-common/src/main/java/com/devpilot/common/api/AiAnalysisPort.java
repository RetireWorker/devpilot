package com.devpilot.common.api;

/**
 * Implemented by devpilot-ai. Kept in common so bug/task modules depend only on
 * the contract.
 */
public interface AiAnalysisPort {

    BugAnalysisSummary analyzeBug(BugAnalysisInput input);
}
