package com.devpilot.repository.workspace;

import com.devpilot.common.api.WorkspaceProvisionRequest;
import com.devpilot.common.api.WorkspaceProvisionResult;
import com.devpilot.common.api.WorkspaceProvisioner;
import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.path.BranchNames;
import com.devpilot.repository.path.PathGuard;
import com.devpilot.repository.process.CommandResult;
import com.devpilot.repository.process.GitCommandRunner;
import com.devpilot.repository.config.RepositoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 基于已存在的镜像仓库创建独立任务工作区。
 * relativeRoot 用于按项目分目录，relativePath 为工作区目录名；两者都必须在根目录内。
 */
@Component
public class WorkspaceManager implements WorkspaceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    /**
     * 构建产物属于本地运行状态，不能进入任务提交或变更审查。
     * 写入 worktree 私有的 info/exclude，而不是改写被跟踪的 .gitignore。
     */
    private static final List<String> BUILD_EXCLUDES = List.of(
            "# devpilot: build outputs are never part of a task change",
            "target/",
            "build/",
            "out/",
            "dist/",
            "node_modules/",
            ".idea/",
            ".vscode/",
            "*.class");

    private final GitCommandRunner runner;
    private final RepositoryProperties properties;

    public WorkspaceManager(GitCommandRunner runner, RepositoryProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public WorkspaceProvisionResult provision(WorkspaceProvisionRequest request) {
        if (request == null) {
            throw new ValidationException("工作区请求不能为空");
        }
        if (request.taskId() == null || request.taskId().isBlank()) {
            throw new ValidationException("taskId 不能为空");
        }
        var relativeRoot = request.relativeRoot() == null || request.relativeRoot().isBlank()
                ? "workspaces" : request.relativeRoot();
        var relativePath = request.relativePath() == null || request.relativePath().isBlank()
                ? request.workspaceId() : request.relativePath();
        if (relativePath == null || relativePath.isBlank()) {
            throw new ValidationException("workspaceId 不能为空");
        }

        var projectRoot = PathGuard.resolveUnder(properties.root(), relativeRoot);
        var target = PathGuard.resolveUnder(projectRoot, relativePath);
        var branch = branchOf(request);

        if (Files.isDirectory(target)) {
            applyBuildExcludes(target);
            var head = headOf(target);
            return new WorkspaceProvisionResult(request.workspaceId(),
                    properties.root().toAbsolutePath().normalize().relativize(target).toString(),
                    branch, request.baseRef(), head, false);
        }
        try {
            Files.createDirectories(target.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("无法创建工作区目录: " + target.getParent(), e);
        }
        var ref = request.baseRef() == null || request.baseRef().isBlank() ? "HEAD" : request.baseRef();
        var mirror = mirrorOf(request);
        var result = run(mirror, List.of(properties.gitBin(), "worktree", "add", "-b", branch,
                target.toAbsolutePath().toString(), ref));
        if (!result.success()) {
            var output = result.combinedOutput();
            var trimmed = output.length() > 2000 ? output.substring(0, 2000) + "...(截断)" : output;
            throw new IllegalStateException("git worktree add 失败: " + trimmed);
        }
        var head = headOf(target);
        applyBuildExcludes(target);
        return new WorkspaceProvisionResult(request.workspaceId(),
                properties.root().toAbsolutePath().normalize().relativize(target).toString(),
                branch, ref, head, true);
    }

    private void applyBuildExcludes(Path worktree) {
        var result = run(worktree, List.of(properties.gitBin(), "rev-parse", "--git-path", "info/exclude"));
        if (!result.success()) {
            log.warn("cannot resolve git exclude path for {}: {}", worktree, result.combinedOutput().trim());
            return;
        }
        var exclude = worktree.resolve(result.stdout().trim()).normalize();
        try {
            Files.createDirectories(exclude.getParent());
            var existing = Files.exists(exclude)
                    ? Files.readString(exclude, StandardCharsets.UTF_8) : "";
            var existingLines = existing.lines().toList();
            var missing = BUILD_EXCLUDES.stream()
                    .filter(line -> !existingLines.contains(line))
                    .toList();
            if (missing.isEmpty()) {
                return;
            }
            var prefix = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
            Files.writeString(exclude, prefix + String.join("\n", missing) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("cannot write build excludes for {}: {}", worktree, e.getMessage());
        }
    }

    private String branchOf(WorkspaceProvisionRequest request) {
        return request.branch() == null || request.branch().isBlank()
                ? BranchNames.taskBranch(request.taskId()) : request.branch();
    }

    /**
     * worktree 必须从镜像仓库根执行；relativeRoot 若指向镜像目录则直接使用，
     * 否则回退到镜像根下的项目目录。
     */
    private Path mirrorOf(WorkspaceProvisionRequest request) {
        var relative = request.relativeRoot() == null || request.relativeRoot().isBlank()
                ? request.projectId() : request.relativeRoot();
        var mirror = PathGuard.resolveUnder(properties.mirrorRoot(), relative);
        if (Files.isDirectory(mirror.resolve(".git"))) {
            return mirror;
        }
        return PathGuard.resolveUnder(properties.mirrorRoot(), request.projectId());
    }

    private String headOf(Path target) {
        var result = runner.run(target, List.of(properties.gitBin(), "rev-parse", "HEAD"),
                properties.commandTimeout());
        if (!result.success()) {
            return null;
        }
        return result.stdout().trim();
    }

    private CommandResult run(Path directory, List<String> argv) {
        return runner.run(directory, argv, properties.commandTimeout());
    }
}
