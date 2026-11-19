package com.devpilot.common.api;

import java.util.List;

public record CodeChangeView(
        String taskId,
        String workspaceId,
        String branch,
        String commitSha,
        String baseRef,
        List<ChangedFile> files,
        int insertions,
        int deletions) {

    public record ChangedFile(String path, String changeType, int insertions, int deletions) {
    }
}
