package com.devpilot.common.api;

import java.util.List;
import java.util.Optional;

public interface ProjectCatalog {

    Optional<ProjectDescriptor> findById(String projectId);

    List<ProjectDescriptor> listActive();
}
