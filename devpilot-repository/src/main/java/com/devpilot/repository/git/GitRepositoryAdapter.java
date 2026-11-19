package com.devpilot.repository.git;

import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.DiffStat;
import com.devpilot.common.api.RepositoryWorkspace;
import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.config.RepositoryProperties;
import com.devpilot.repository.path.BranchNames;
import com.devpilot.repository.path.PathGuard;
import com.devpilot.repository.process.CommandResult;
import com.devpilot.repository.process.GitCommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 原生 Git 仓库操作。克隆走镜像目录，任务开发一律使用 worktree，
 * 保证同一仓库可并行多个任务且互不污染工作区。
 */
@Component
public class GitRepositoryAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryAdapter.class);

    private final GitCommandRunner runner;
    private final RepositoryProperties properties;

    public GitRepositoryAdapter(GitCommandRunner runner, RepositoryProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    /**
     * 镜像不存在时 clone 为 bare 仓库；已存在则 fetch 最新引用。
     */
    public Path ensureMirror(String projectId, String repositoryUrl, String defaultBranch) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new ValidationException("Git 仓库地址不能为空");
        }
        var mirror = PathGuard.resolveUnder(properties.mirrorRoot(), safeName(projectId));
        if (Files.isDirectory(mirror.resolve("objects"))) {
            var fetched = run(mirror, List.of(git(), "fetch", "--prune", "origin"));
            requireSuccess(fetched, "git fetch 失败");
            return mirror;
        }

        var parent = mirror.getParent();
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建镜像目录: " + parent, e);
        }
        var result = run(parent, List.of(git(), "clone", "--mirror", "--", repositoryUrl,
                mirror.getFileName().toString()));
        requireSuccess(result, "git clone --mirror 失败");
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            log.debug("镜像 {} 使用默认分支 {}", projectId, defaultBranch);
        }
        return mirror;
    }

    /**
     * 基于镜像创建任务 worktree。分支名固定 ai/&lt;taskId&gt;，重名时先删除旧工作区引用。
     */
    public RepositoryWorkspace createWorktree(String projectId, String taskId, String workspaceId,
                                              Path mirror, String baseRef, String branch) {
        var targetBranch = branch == null || branch.isBlank() ? BranchNames.taskBranch(taskId) : branch;
        var relative = safeName(projectId) + "/" + safeName(workspaceId);
        var worktreePath = PathGuard.resolveUnder(properties.root(), relative);
        if (Files.isDirectory(worktreePath)) {
            return new RepositoryWorkspace(workspaceId, projectId, taskId, worktreePath, targetBranch, baseRef);
        }
        try {
            Files.createDirectories(worktreePath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("无法创建工作区父目录: " + worktreePath.getParent(), e);
        }
        var ref = baseRef == null || baseRef.isBlank() ? "HEAD" : baseRef;
        var result = run(mirror, List.of(git(), "worktree", "add", "-b", targetBranch,
                worktreePath.toAbsolutePath().toString(), ref));
        if (!result.success() && result.combinedOutput().contains("already exists")) {
            var retry = run(mirror, List.of(git(), "worktree", "add",
                    worktreePath.toAbsolutePath().toString(), targetBranch));
            requireSuccess(retry, "git worktree add 失败: " + targetBranch);
        } else {
            requireSuccess(result, "git worktree add 失败");
        }
        return new RepositoryWorkspace(workspaceId, projectId, taskId, worktreePath, targetBranch, ref);
    }

    public void removeWorktree(Path mirror, Path worktreePath) {
        var result = run(mirror, List.of(git(), "worktree", "remove", "--force",
                worktreePath.toAbsolutePath().toString()));
        if (!result.success()) {
            log.warn("删除 worktree 失败: {}", result.combinedOutput());
        }
        run(mirror, List.of(git(), "worktree", "prune"));
    }

    public DiffStat diffStat(Path worktree) {
        var result = run(worktree, List.of(git(), "diff", "--numstat", "HEAD"));
        requireSuccess(result, "git diff --numstat 失败");
        return DiffStatParser.parseNumstat(result.stdout());
    }

    public boolean hasUncommittedChanges(Path worktree) {
        var result = run(worktree, List.of(git(), "status", "--porcelain"));
        requireSuccess(result, "git status 失败");
        return !result.stdout().isBlank();
    }

    public String currentHead(Path worktree) {
        var result = run(worktree, List.of(git(), "rev-parse", "HEAD"));
        requireSuccess(result, "git rev-parse 失败");
        return result.stdout().trim();
    }

    public CodeChangeView commitAll(Path worktree, String workspaceId, String taskId,
                                    String branch, String baseRef, String message) {
        if (message == null || message.isBlank()) {
            throw new ValidationException("提交信息不能为空");
        }
        var add = run(worktree, List.of(git(), "add", "-A"));
        requireSuccess(add, "git add 失败");
        var commit = run(worktree, List.of(git(), "commit", "-m", message));
        requireSuccess(commit, "git commit 失败");
        var head = currentHead(worktree);
        var stat = diffStatAgainst(worktree, baseRef);
        var files = stat.files().stream()
                .map(path -> new CodeChangeView.ChangedFile(path, "modified", 0, 0))
                .toList();
        return new CodeChangeView(taskId, workspaceId, branch, head, baseRef, files,
                stat.insertions(), stat.deletions());
    }

    public DiffStat diffStatAgainst(Path worktree, String baseRef) {
        var ref = baseRef == null || baseRef.isBlank() ? "HEAD~1" : baseRef;
        var result = run(worktree, List.of(git(), "diff", "--numstat", ref, "HEAD"));
        requireSuccess(result, "git diff --numstat 失败");
        return DiffStatParser.parseNumstat(result.stdout());
    }

    protected CommandResult run(Path directory, List<String> argv) {
        return runner.run(directory, argv, properties.commandTimeout());
    }

    private String git() {
        return properties.gitBin();
    }

    static String safeName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("名称不能为空");
        }
        var trimmed = value.trim();
        for (String segment : trimmed.split("[/\\\\]+")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new ValidationException("名称不允许包含路径穿越片段: " + value);
            }
        }
        if (trimmed.contains("..")) {
            throw new ValidationException("名称不允许包含 '..': " + value);
        }
        var sanitized = trimmed.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new ValidationException("名称无法转换为安全目录名: " + value);
        }
        return sanitized;
    }

    protected static void requireSuccess(CommandResult result, String message) {
        if (!result.success()) {
            var output = result.combinedOutput();
            var trimmed = output.length() > 2000 ? output.substring(0, 2000) + "...(截断)" : output;
            throw new IllegalStateException(message + ": " + trimmed);
        }
    }
}
