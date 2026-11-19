package com.devpilot.repository.path;

import java.util.Locale;

public final class BranchNames {

    private BranchNames() {
    }

    /**
     * 任务分支固定为 ai/&lt;taskId&gt;，只保留安全字符，避免 git ref 非法字符与注入。
     */
    public static String taskBranch(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        var sanitized = taskId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("[_-]{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new IllegalArgumentException("taskId 无法转换为合法分支名: " + taskId);
        }
        return "ai/" + sanitized;
    }
}
