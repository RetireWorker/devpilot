package com.devpilot.task.port;

import com.devpilot.common.api.ProjectDescriptor;

import java.util.Optional;

public interface ProjectLookupPort {

    Optional<ProjectDescriptor> findById(String projectId);
}
