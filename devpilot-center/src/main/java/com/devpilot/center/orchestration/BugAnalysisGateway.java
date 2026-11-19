package com.devpilot.center.orchestration;

import com.devpilot.common.api.BugAnalysisSummary;

public interface BugAnalysisGateway {

    BugAnalysisSummary requireAnalysis(String bugId);
}
