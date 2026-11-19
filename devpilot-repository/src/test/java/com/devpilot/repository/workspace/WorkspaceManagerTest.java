package com.devpilot.repository.workspace;

import com.devpilot.common.api.WorkspaceProvisionRequest;
import com.devpilot.repository.config.RepositoryProperties;
import com.devpilot.repository.process.CommandResult;
import com.devpilot.repository.process.GitCommandRunner;
import com.devpilot.repository.process.ProcessGitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceManagerTest {

    @TempDir
    Path root;

    @Test
    void newWorkspaceIgnoresBuildOutputs() throws Exception {
        var mirror = createMirror();
        var properties = new RepositoryProperties(root.resolve("workspaces"),
                root.resolve("mirrors"), "git", "svn", Duration.ofSeconds(30));
        var manager = new WorkspaceManager(new ProcessGitCommandRunner(), properties);

        var result = manager.provision(new WorkspaceProvisionRequest(
                "T1", "demo", "ws-1", "ai/t1", "HEAD", "demo", "ws-1"));

        assertThat(result.created()).isTrue();
        Path worktree = root.resolve("workspaces/demo/ws-1");
        assertThat(Files.readString(excludePath(worktree), StandardCharsets.UTF_8))
                .contains("target/");

        Files.createDirectories(worktree.resolve("target/classes"));
        Files.writeString(worktree.resolve("target/classes/Junk.class"), "x");
        assertThat(git(worktree, "status", "--porcelain", "--untracked-files=all").stdout()).isEmpty();
    }

    @Test
    void existingWorkspaceIsMigratedWithExcludes() throws Exception {
        var mirror = createMirror();
        var properties = new RepositoryProperties(root.resolve("workspaces"),
                root.resolve("mirrors"), "git", "svn", Duration.ofSeconds(30));
        var manager = new WorkspaceManager(new ProcessGitCommandRunner(), properties);
        manager.provision(new WorkspaceProvisionRequest(
                "T1", "demo", "ws-1", "ai/t1", "HEAD", "demo", "ws-1"));
        Path worktree = root.resolve("workspaces/demo/ws-1");
        var exclude = excludePath(worktree);
        Files.writeString(exclude, "");

        manager.provision(new WorkspaceProvisionRequest(
                "T1", "demo", "ws-1", "ai/t1", "HEAD", "demo", "ws-1"));

        assertThat(Files.readString(exclude, StandardCharsets.UTF_8)).contains("target/");
    }

    private Path createMirror() throws Exception {
        Path mirror = Files.createDirectories(root.resolve("mirrors/demo"));
        git(mirror, "init", "-q", "-b", "main");
        Files.writeString(mirror.resolve("README.md"), "demo\n");
        git(mirror, "add", "README.md");
        git(mirror, "-c", "user.name=test", "-c", "user.email=test@localhost",
                "commit", "-q", "-m", "init");
        return mirror;
    }

    private Path excludePath(Path worktree) {
        return Path.of(git(worktree, "rev-parse", "--git-path", "info/exclude").stdout().trim());
    }

    private CommandResult git(Path dir, String... args) {
        GitCommandRunner runner = new ProcessGitCommandRunner();
        var argv = new java.util.ArrayList<String>();
        argv.add("git");
        argv.addAll(List.of(args));
        return runner.run(dir, argv, Duration.ofSeconds(30));
    }
}