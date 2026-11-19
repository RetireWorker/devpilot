package com.devpilot.task.port;

import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.TaskPlanView;

public interface TaskPlanPort {

    TaskPlanView generate(BugAnalysisSummary bug, ProjectDescriptor project, double confidence);
}
