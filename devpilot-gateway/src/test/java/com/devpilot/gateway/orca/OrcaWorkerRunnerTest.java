package com.devpilot.gateway.orca;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentRole;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.gateway.config.GatewayProperties;
import com.devpilot.gateway.execution.ExecutionRecord;
import com.devpilot.gateway.execution.ExecutionRegistry;
import com.devpilot.gateway.execution.LogStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrcaWorkerRunnerTest {

    @TempDir
    Path tempDir;

    private GatewayProperties properties;
    private ObjectMapper mapper;
    private ExecutionRegistry registry;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper().findAndRegisterModules();
        properties = new GatewayProperties();
        properties.setEnabled(true);
        properties.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        properties.setStateDir(tempDir.resolve("state").toString());
        properties.setWorkerPollInterval(Duration.ofMillis(50));
        properties.setWorkerDrainWindow(Duration.ofMillis(100));
        properties.setDefaultTimeout(Duration.ofSeconds(20));
        properties.setHideWorkspaceWhenDone(false);
        properties.setAutoCommit(true);
        registry = new ExecutionRegistry(properties, mapper);
        workspace = tempDir.resolve("workspaces/task-1");
        Files.createDirectories(workspace);
        git(workspace, "init", "-q");
        Files.writeString(workspace.resolve("README.md"), "base\n");
        git(workspace, "add", ".");
        git(workspace, "commit", "-qm", "init");
    }

    @Test
    void successfulWorkerCommitsInspectedChange() throws Exception {
        properties.setOrcaBinary(fakeOrca(
                "echo changed >> feature.txt",
                "succeeded",
                "{\"summary\":\"fix duplicate sync\",\"changedFiles\":[\"feature.txt\"]}").toString());

        ExecutionRecord record = execute(List.of("feature.txt"), List.of(".git/**"));

        assertThat(record.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(record.getExitCode()).isZero();
        Map<String, Object> output = mapper.readValue(record.getResultJson(), Map.class);
        assertThat((List<?>) output.get("changedFiles")).anyMatch("feature.txt"::equals);
        assertThat(output.get("commitSha")).isNotNull();
        assertThat(output.get("summary")).isEqualTo("fix duplicate sync");
        assertThat(lastCommitMessage(workspace)).contains("[BUG-1]");
        assertThat(record.getDispatchId()).isEqualTo("ctx_test");
    }

    @Test
    void changeOutsideAllowedScopeFailsWithoutCommit() throws Exception {
        properties.setOrcaBinary(fakeOrca(
                "echo oops > evil.txt",
                "succeeded",
                "{\"summary\":\"did work\",\"changedFiles\":[\"evil.txt\"]}").toString());
        String before = head(workspace);

        ExecutionRecord record = execute(List.of("src/**"), List.of());

        assertThat(record.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        Map<String, Object> output = mapper.readValue(record.getResultJson(), Map.class);
        assertThat((List<?>) output.get("violations"))
                .anyMatch(item -> String.valueOf(item).contains("evil.txt"));
        assertThat(head(workspace)).isEqualTo(before);
    }

    @Test
    void idleChatWorkerCompletesFromStructuredOutput() throws Exception {
        properties.setWorkerIdleSettle(Duration.ofMillis(100));
        properties.setOrcaBinary(fakeOrca("echo idle >> feature.txt", "ready",
                "{\"summary\":\"done via idle\",\"changedFiles\":[\"feature.txt\"]}").toString());

        ExecutionRecord record = execute(List.of("feature.txt"), List.of());

        assertThat(record.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        Map<String, Object> output = mapper.readValue(record.getResultJson(), Map.class);
        assertThat(output.get("summary")).isEqualTo("done via idle");
        assertThat(output.get("commitSha")).isNotNull();
    }

    @Test
    void failedWorkerIsReportedAsFailed() throws Exception {
        properties.setOrcaBinary(fakeOrca("true", "failed",
                "{\"summary\":\"cannot fix\",\"changedFiles\":[]}").toString());

        ExecutionRecord record = execute(List.of(), List.of());

        assertThat(record.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(record.getExitCode()).isEqualTo(1);
    }

    private ExecutionRecord execute(List<String> allowed, List<String> forbidden) {
        var cli = new OrcaCli(properties, mapper);
        var runner = new OrcaWorkerRunner(cli, new WorkerSpecFactory(), new WorkspaceInspector(),
                new StructuredOutputParser(mapper), registry, properties, mapper);
        var request = new AgentExecutionRequest(
                "key-" + System.nanoTime(), "BUG-1", "fix-code", "proj", "ws-1", "task-1",
                AgentRole.JAVA_DEVELOPER, "修复重复数据问题", allowed, forbidden,
                List.of("bug-analysis"), Map.of(), Duration.ofSeconds(20), Map.of());
        var record = new ExecutionRecord();
        record.setExecutionId("EX-" + System.nanoTime());
        record.setExecutionKey(request.executionKey());
        record.setTaskId(request.taskId());
        record.setSubtaskId(request.subtaskId());
        record.setProjectId(request.projectId());
        record.setWorkspaceId(request.workspaceId());
        record.setRelativeWorkspace("task-1");
        record.setKind("agent");
        record.setCommandKey(request.role().name());
        record.setStatus(ExecutionStatus.PENDING);
        record.setCreatedAt(Instant.now());
        record.setLogFile(new LogStore(properties).newLogFile(record.getExecutionId()).toString());
        registry.register(record);

        runner.run(record, request, workspace, Duration.ofSeconds(20));
        return record;
    }

    private Path fakeOrca(String action, String finalState, String finalAnswer) throws IOException {
        Path script = tempDir.resolve("orca-fake-" + System.nanoTime() + ".sh");
        Map<String, Object> message = Map.of(
                "role", "assistant",
                "blocks", List.of(Map.of("type", "text",
                        "text", "done\n```json\n" + finalAnswer + "\n```")));
        Map<String, Object> result = Map.of(
                "dispatchId", "ctx_test",
                "cursor", "c1",
                "transcript", Map.of("messages", List.of(message), "nextCursor", "c1"),
                "projection", Map.of("outcome", finalState,
                        "attention", Map.of("categories", List.of(), "requiresAction", false)));
        String readJson = mapper.writeValueAsString(Map.of("ok", true, "result", result));
        String currentJson = mapper.writeValueAsString(Map.of("ok", true, "result", Map.of(
                "worktree", Map.of("id", "fake::" + workspace, "path", workspace.toString()))));
        String content = """
                #!/bin/sh
                case "$1 $2" in
                  "worktree current")
                    printf '%%s\\n' '%s'; exit 0;;
                  "repo add"*) echo '{"ok":true,"result":{"repo":{"id":"fake"}}}'; exit 0;;
                  "terminal create") echo '{"ok":true,"result":{"terminal":{"handle":"term_fake"}}}'; exit 0;;
                  "terminal close") echo '{"ok":true}'; exit 0;;
                  "orchestration worker-start")
                    case "$*" in *"--worktree id:"*) ;; *)
                      echo '{"ok":false,"error":{"message":"expected exact id selector"}}'; exit 1;; esac
                    %s
                    echo '{"ok":true,"result":{"dispatchId":"ctx_test","state":"ready"}}'; exit 0;;
                  "orchestration worker-show")
                    echo '{"ok":true,"result":{"state":"%s","stage":"settled","activity":"idle"}}'; exit 0;;
                  "orchestration worker-read")
                    printf '%%s\\n' '%s'; exit 0;;
                  "orchestration worker-stop"|"orchestration worker-release")
                    echo '{"ok":true}'; exit 0;;
                esac
                echo '{"ok":false,"error":{"message":"unexpected command: $*"}}' >&2
                exit 2
                """.formatted(currentJson, action, finalState, readJson);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }

    private void git(Path dir, String... args) throws Exception {
        var argv = new java.util.ArrayList<String>();
        argv.add("git");
        argv.add("-C");
        argv.add(dir.toString());
        argv.addAll(List.of(args));
        var process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        process.getInputStream().readNBytes(1_000_000);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args));
        }
    }

    private String head(Path dir) throws Exception {
        var process = new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
        String sha = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return sha;
    }

    private String lastCommitMessage(Path dir) throws Exception {
        var process = new ProcessBuilder("git", "-C", dir.toString(), "log", "-1", "--pretty=%s")
                .redirectErrorStream(true).start();
        String message = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return message;
    }
}