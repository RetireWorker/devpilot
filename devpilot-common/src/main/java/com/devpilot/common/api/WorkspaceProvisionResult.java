package com.devpilot.common.api;

public record WorkspaceProvisionResult(
        String workspaceId,
        String relativePath,
        String branch,
        String baseRef,
        String headCommit,
        boolean created) {
}
