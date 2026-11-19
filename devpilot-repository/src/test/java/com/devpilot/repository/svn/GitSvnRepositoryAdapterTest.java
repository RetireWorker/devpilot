package com.devpilot.repository.svn;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.config.RepositoryProperties;
import com.devpilot.repository.lock.InMemoryRepositoryLock;
import com.devpilot.repository.process.CommandResult;
import com.devpilot.repository.process.GitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitSvnRepositoryAdapterTest {

    @TempDir
    Path root;

    private RepositoryProperties props() {
        return new RepositoryProperties(root.resolve("ws"), root.resolve("mirrors"),
                "git", "svn", Duration.ofSeconds(5));
    }

    @Test
    void dcommitRunsFetchThenDcommitAndReadsRevision() throws Exception {
        var calls = new CopyOnWriteArrayList<String>();
        var runner = new RecordingRunner(calls, "r12345");
        var adapter = new GitSvnRepositoryAdapter(runner, props(), new InMemoryRepositoryLock());
        var mirror = Files.createDirectories(root.resolve("mirrors/svn-demo"));
        var worktree = Files.createDirectories(root.resolve("ws/demo/ws-1"));

        var result = adapter.dcommit(worktree, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(true, true));

        assertThat(result.revision()).isEqualTo("r12345");
        var fetch = indexOf(calls, "svn fetch");
        var revList = indexOf(calls, "rev-list");
        var dcommit = indexOf(calls, "svn dcommit");
        var info = indexOf(calls, "svn info");
        assertThat(fetch).isLessThan(revList);
        assertThat(revList).isLessThan(dcommit);
        assertThat(dcommit).isLessThan(info);
    }

    @Test
    void dcommitRefusedWhenTestsFailedAndNoCommandRuns() throws Exception {
        var calls = new CopyOnWriteArrayList<String>();
        var adapter = new GitSvnRepositoryAdapter(new RecordingRunner(calls, "r1"), props(),
                new InMemoryRepositoryLock());
        var mirror = Files.createDirectories(root.resolve("mirrors/svn-demo"));
        var worktree = Files.createDirectories(root.resolve("ws/demo/ws-1"));

        assertThatThrownBy(() -> adapter.dcommit(worktree, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(false, true)))
                .isInstanceOf(ValidationException.class);
        assertThat(calls).doesNotContain("svn dcommit");
    }

    @Test
    void dcommitRefusedWhenCommitRangeEmpty() throws Exception {
        var calls = new CopyOnWriteArrayList<String>();
        var adapter = new GitSvnRepositoryAdapter(new RecordingRunner(calls, "r1", 0), props(),
                new InMemoryRepositoryLock());
        var mirror = Files.createDirectories(root.resolve("mirrors/svn-demo"));
        var worktree = Files.createDirectories(root.resolve("ws/demo/ws-1"));

        assertThatThrownBy(() -> adapter.dcommit(worktree, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(true, true)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("没有待回写");
        assertThat(calls).doesNotContain("svn dcommit");
    }

    @Test
    void concurrentDcommitIsRejectedByRepositoryLock() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var calls = new CopyOnWriteArrayList<String>();
        GitCommandRunner blockingRunner = (dir, argv, timeout) -> {
            var joined = String.join(" ", argv);
            if (joined.contains("svn fetch")) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            calls.add(joined);
            if (joined.contains("rev-list")) {
                return new CommandResult(0, "1\n", "", false);
            }
            if (joined.contains("svn info")) {
                return new CommandResult(0, "URL: https://svn.example/repo\nRevision: 99\n", "", false);
            }
            return new CommandResult(0, "", "", false);
        };
        var lock = new InMemoryRepositoryLock();
        var adapter = new GitSvnRepositoryAdapter(blockingRunner, props(), lock);
        var mirror = Files.createDirectories(root.resolve("mirrors/svn-demo"));
        var worktreeA = Files.createDirectories(root.resolve("ws/demo/a"));
        var worktreeB = Files.createDirectories(root.resolve("ws/demo/b"));

        var thread = new Thread(() -> adapter.dcommit(worktreeA, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(true, true)));
        thread.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> adapter.dcommit(worktreeB, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(true, true)))
                .isInstanceOf(ConflictException.class);

        release.countDown();
        thread.join(5_000);
        assertThat(thread.isAlive()).isFalse();
        assertThat(lock.tryAcquire(mirror.toAbsolutePath().normalize().toString())).isTrue();
        lock.release(mirror.toAbsolutePath().normalize().toString());
    }

    @Test
    void lockReleasedWhenDcommitFails() throws Exception {
        var calls = new CopyOnWriteArrayList<String>();
        GitCommandRunner failing = (dir, argv, timeout) -> {
            var joined = String.join(" ", argv);
            calls.add(joined);
            if (joined.contains("rev-list")) {
                return new CommandResult(0, "1\n", "", false);
            }
            if (joined.contains("svn dcommit")) {
                return new CommandResult(1, "", "svn: E170001 authentication failed", false);
            }
            return new CommandResult(0, "", "", false);
        };
        var lock = new InMemoryRepositoryLock();
        var adapter = new GitSvnRepositoryAdapter(failing, props(), lock);
        var mirror = Files.createDirectories(root.resolve("mirrors/svn-demo"));
        var worktree = Files.createDirectories(root.resolve("ws/demo/ws-1"));

        assertThatThrownBy(() -> adapter.dcommit(worktree, mirror, "refs/remotes/svn/trunk",
                new SvnDcommitGuard(true, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dcommit 失败");

        assertThat(lock.tryAcquire(mirror.toAbsolutePath().normalize().toString())).isTrue();
        lock.release(mirror.toAbsolutePath().normalize().toString());
    }

    private static final class RecordingRunner implements GitCommandRunner {
        private final List<String> calls;
        private final String revisionOutput;
        private final int commitCount;

        private RecordingRunner(List<String> calls, String revisionOutput) {
            this(calls, revisionOutput, 1);
        }

        private RecordingRunner(List<String> calls, String revisionOutput, int commitCount) {
            this.calls = calls;
            this.revisionOutput = revisionOutput;
            this.commitCount = commitCount;
        }

        @Override
        public CommandResult run(Path workingDirectory, List<String> argv, Duration timeout) {
            var joined = String.join(" ", argv);
            calls.add(joined);
            if (joined.contains("rev-list")) {
                return new CommandResult(0, commitCount + "\n", "", false);
            }
            if (joined.contains("svn info")) {
                return new CommandResult(0, "URL: https://svn.example/repo\nRevision: "
                        + revisionOutput.substring(1) + "\n", "", false);
            }
            return new CommandResult(0, "", "", false);
        }
    }

    private static int indexOf(List<String> calls, String fragment) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
