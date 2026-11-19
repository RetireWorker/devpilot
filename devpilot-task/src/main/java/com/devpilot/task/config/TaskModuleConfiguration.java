package com.devpilot.task.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers task-module properties. Ports (TaskPlanPort, TaskWorkspacePort,
 * ProjectLookupPort) are supplied by the center application.
 */
@Configuration
@EnableConfigurationProperties(TaskProperties.class)
public class TaskModuleConfiguration {
}
