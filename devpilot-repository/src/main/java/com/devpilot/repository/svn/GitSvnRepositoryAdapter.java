package com.devpilot.repository.svn;

import com.devpilot.common.api.RepositoryWorkspace;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.config.RepositoryProperties;
import com.devpilot.repository.git.GitRepositoryAdapter;
import com.devpilot.repository.lock.RepositoryLock;
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
import java.util.regex.Pattern;

/**
 * Git-SVN 适配器：本地镜像使用 Git 分支/Worktree 开发，审批后串行回写 SVN。
 *
 * <p>并发约束：同一 SVN 仓库的 fetch 与 dcommit 共享一把锁，避免元数据竞争；
 * 不同任务可并行开发，但回写必须串行。
 */
@Component
public class GitSvnRepositoryAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitSvnRepositoryAdapter.class);
    /**
     * git svn info 输出 “Revision: 12345”，dcommit 日志输出 “Committed r12345”，两种都解析。
     */
    private static final Pattern REVISION = Pattern.compile(
            "(?:^Revision:\\s*|-?\\br)(\\d+)\\b", Pattern.MULTILINE);

    private final GitCommandRunner runner;
    private final RepositoryProperties properties;
    private final RepositoryLock lock;

    public GitSvnRepositoryAdapter(GitCommandRunner runner, RepositoryProperties properties,
                                   RepositoryLock lock) {
        this.runner = runner;
        this.properties = properties;
        this.lock = lock;
    }

    public Path ensureMirror(String projectId, String svnUrl, String layout,
                             String trunk, String branches, String tags) {
        if (svnUrl == null || svnUrl.isBlank()) {
            throw new ValidationException("SVN 地址不能为空");
        }
        var mirror = PathGuard.resolveUnder(properties.mirrorRoot(), "svn-" + safeName(projectId));
        if (Files.isDirectory(mirror.resolve(".git"))) {
            fetch(mirror);
            return mirror;
        }
        var parent = mirror.getParent();
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建镜像目录: " + parent, e);
        }
        var argv = new ArrayList<String>();
        argv.add(git());
        argv.add("svn");
        argv.add("clone");
        if ("standard".equalsIgnoreCase(layout)) {
            argv.add("--stdlayout");
        } else {
            argv.add("-T");
            argv.add(trunk == null || trunk.isBlank() ? "trunk" : trunk);
            argv.add("-b");
            argv.add(branches == null || branches.isBlank() ? "branches" : branches);
            argv.add("-t");
            argv.add(tags == null || tags.isBlank() ? "tags" : tags);
        }
        argv.add("--prefix=svn/");
        argv.add("--");
        argv.add(svnUrl);
        argv.add(mirror.getFileName().toString());
        var result = runner.run(parent, argv, properties.commandTimeout());
        requireSuccess(result, "git svn clone 失败");
        return mirror;
    }

    /**
     * fetch 会刷新 SVN 元数据，必须串行；拿不到锁直接报冲突而不是排队等待。
     */
    public void fetch(Path mirror) {
        var key = lockKey(mirror);
        if (!lock.tryAcquire(key)) {
            throw new ConflictException("仓库正在同步，请稍后重试: " + mirror.getFileName());
        }
        try {
            requireSuccess(run(mirror, List.of(git(), "svn", "fetch")), "git svn fetch 失败");
        } finally {
            lock.release(key);
        }
    }

    public RepositoryWorkspace createWorktree(String projectId, String taskId, String workspaceId,
                                              Path mirror, String targetRef, String branch) {
        var targetBranch = branch == null || branch.isBlank() ? BranchNames.taskBranch(taskId) : branch;
        var relative = safeName(projectId) + "/" + safeName(workspaceId);
        var worktreePath = PathGuard.resolveUnder(properties.root(), relative);
        if (Files.isDirectory(worktreePath)) {
            return new RepositoryWorkspace(workspaceId, projectId, taskId, worktreePath, targetBranch, targetRef);
        }
        try {
            Files.createDirectories(worktreePath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("无法创建工作区父目录: " + worktreePath.getParent(), e);
        }
        var ref = targetRef == null || targetRef.isBlank() ? "refs/remotes/svn/trunk" : targetRef;
        var result = run(mirror, List.of(git(), "worktree", "add", "-b", targetBranch,
                worktreePath.toAbsolutePath().toString(), ref));
        requireSuccess(result, "git worktree add 失败: " + targetBranch);
        return new RepositoryWorkspace(workspaceId, projectId, taskId, worktreePath, targetBranch, ref);
    }

    /**
     * 回写 SVN。调用方必须先完成测试、审查和审批；本方法只负责加锁与执行。
     * 步骤：加锁 -> fetch -> 校验提交范围 -> dcommit -> 读取 revision。
     */
    public GitSvnDcommitResult dcommit(Path worktree, Path mirror, String svnRemoteRef,
                                       SvnDcommitGuard guard) {
        var dirty = hasUncommittedChanges(worktree);
        guard.assertReady(dirty);

        var key = lockKey(mirror);
        if (!lock.tryAcquire(key)) {
            throw new ConflictException("仓库正在同步或回写，请稍后重试: " + mirror.getFileName());
        }
        try {
            requireSuccess(run(mirror, List.of(git(), "svn", "fetch")), "dcommit 前 git svn fetch 失败");
            verifyCommitRange(worktree, svnRemoteRef);
            var result = run(worktree, List.of(git(), "svn", "dcommit"));
            requireSuccess(result, "git svn dcommit 失败");
            var revision = readRevision(worktree);
            log.info("Git-SVN dcommit 完成，revision={}", revision);
            return new GitSvnDcommitResult(revision, result.stdout());
        } finally {
            lock.release(key);
        }
    }

    public String readRevision(Path worktree) {
        var result = run(worktree, List.of(git(), "svn", "info"));
        requireSuccess(result, "git svn info 失败");
        var matcher = REVISION.matcher(result.stdout());
        if (!matcher.find()) {
            throw new IllegalStateException("无法从 git svn info 解析 revision");
        }
        return "r" + matcher.group(1);
    }

    private void verifyCommitRange(Path worktree, String svnRemoteRef) {
        var ref = svnRemoteRef == null || svnRemoteRef.isBlank() ? "refs/remotes/svn/trunk" : svnRemoteRef;
        var countResult = run(worktree, List.of(git(), "rev-list", "--count", ref + "..HEAD"));
        requireSuccess(countResult, "检查提交范围失败");
        var count = Integer.parseInt(countResult.stdout().trim());
        if (count <= 0) {
            throw new ValidationException("当前分支没有待回写的 SVN 提交");
        }
        var logResult = run(worktree, List.of(git(), "log", "--oneline", "--merges", ref + "..HEAD"));
        requireSuccess(logResult, "检查合并提交失败");
        if (!logResult.stdout().isBlank()) {
            throw new ValidationException("待回写提交包含合并提交，需先整理提交历史");
        }
    }

    private boolean hasUncommittedChanges(Path worktree) {
        var result = run(worktree, List.of(git(), "status", "--porcelain"));
        requireSuccess(result, "git status 失败");
        return !result.stdout().isBlank();
    }

    private CommandResult run(Path directory, List<String> argv) {
        return runner.run(directory, argv, properties.commandTimeout());
    }

    private String git() {
        return properties.gitBin();
    }

    private static String lockKey(Path mirror) {
        return mirror.toAbsolutePath().normalize().toString();
    }

    private static String safeName(String value) {
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
        if (sanitized.isEmpty()) {
            throw new ValidationException("名称无法转换为安全目录名: " + value);
        }
        return sanitized;
    }

    private static void requireSuccess(CommandResult result, String message) {
        if (!result.success()) {
            var output = result.combinedOutput();
            var trimmed = output.length() > 2000 ? output.substring(0, 2000) + "...(截断)" : output;
            throw new IllegalStateException(message + ": " + trimmed);
        }
    }
}
