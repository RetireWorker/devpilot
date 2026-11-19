package com.devpilot.common.api;

public interface TaskPlanPort {

    TaskPlanView generate(BugAnalysisSummary summary, ProjectDescriptor project);
}
