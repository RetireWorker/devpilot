package com.devpilot.repository.git;

import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.config.RepositoryProperties;
import com.devpilot.repository.process.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepositoryAdapterTest {

    @TempDir
    Path root;

    @Test
    void rejectsWorkspacePathTraversalWhenCreatingWorktree() throws Exception {
        var mirror = Files.createDirectories(root.resolve("mirrors/demo"));
        var adapter = new GitRepositoryAdapter((dir, argv, timeout) -> new CommandResult(0, "", "", false),
                new RepositoryProperties(root, root.resolve("mirrors"), "git", "svn", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> adapter.createWorktree("../../etc", "BUG-1", "ws-1", mirror, "HEAD", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void reportsDiffStatFromRunnerOutput() throws Exception {
        var mirror = Files.createDirectories(root.resolve("mirrors/demo"));
        var worktree = Files.createDirectories(root.resolve("workspaces/demo/ws-1"));
        var adapter = new GitRepositoryAdapter((dir, argv, timeout) ->
                new CommandResult(0, "3\t1\tsrc/Main.java\n", "", false),
                new RepositoryProperties(root, root.resolve("mirrors"), "git", "svn", Duration.ofSeconds(5)));

        var stat = adapter.diffStat(worktree);

        assertThat(stat.insertions()).isEqualTo(3);
        assertThat(stat.deletions()).isEqualTo(1);
        assertThat(stat.files()).containsExactly("src/Main.java");
        assertThat(mirror).exists();
    }

    @Test
    void surfacesCommandFailureWithBoundedOutput() {
        var adapter = new GitRepositoryAdapter((dir, argv, timeout) ->
                new CommandResult(128, "", "fatal: not a repository", false),
                new RepositoryProperties(root, root.resolve("mirrors"), "git", "svn", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> adapter.currentHead(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git rev-parse");
    }

    @Test
    void sanitizesUnsafeNames() {
        assertThat(GitRepositoryAdapter.safeName("my project/ws 1")).isEqualTo("my-project-ws-1");
        assertThatThrownBy(() -> GitRepositoryAdapter.safeName("my project/../x"))
                .isInstanceOf(ValidationException.class);
    }
}
