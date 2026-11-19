package com.devpilot.common.api;

public interface ProjectMatchPort {

    ProjectMatchResultView match(BugAnalysisSummary summary, String specifiedProjectId);
}
