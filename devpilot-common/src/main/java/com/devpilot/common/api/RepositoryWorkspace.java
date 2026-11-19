package com.devpilot.common.api;

import java.nio.file.Path;

/**
 * A local working copy handled by the repository adapter. Path always lives
 * under a configured workspace root.
 */
public record RepositoryWorkspace(
        String workspaceId,
        String projectId,
        String taskId,
        Path path,
        String branch,
        String baseRef) {
}
