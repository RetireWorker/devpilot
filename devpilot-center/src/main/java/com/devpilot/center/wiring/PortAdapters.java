package com.devpilot.center.wiring;

import com.devpilot.ai.TaskPlanGenerator;
import com.devpilot.bug.BugService;
import com.devpilot.common.api.ProjectCatalog;
import com.devpilot.common.api.WorkspaceProvisioner;
import com.devpilot.task.port.ProjectLookupPort;
import com.devpilot.task.port.ProjectRepositoryPort;
import com.devpilot.task.port.TaskPlanPort;
import com.devpilot.task.port.TaskWorkspacePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Only the center wires ports to implementations. Business modules stay
 * independent and can be tested without the whole application.
 */
@Configuration
public class PortAdapters {

    @Bean
    public ProjectLookupPort projectLookupPort(ProjectCatalog catalog) {
        return catalog::findById;
    }

    @Bean
    public TaskWorkspacePort taskWorkspacePort(WorkspaceProvisioner provisioner) {
        return provisioner::provision;
    }

    @Bean
    public ProjectRepositoryPort projectRepositoryPort(ProjectRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public TaskPlanPort taskPlanPort(TaskPlanGenerator generator) {
        return generator::generate;
    }

    @Bean
    public com.devpilot.center.orchestration.BugAnalysisGateway bugAnalysisGateway(BugService bugService) {
        return bugService::requireAnalysis;
    }
}
