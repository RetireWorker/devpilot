package com.devpilot.gateway.execution;

import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionServiceTest {

    @TempDir
    Path tempDir;

    private ExecutionService service;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("workspaces/task-1"));
        properties = new GatewayProperties();
        properties.setEnabled(true);
        properties.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        properties.setStateDir(tempDir.resolve("state").toString());
        properties.setDefaultTimeout(Duration.ofSeconds(5));
        properties.setCommandTemplates(Map.of(
                "quick", List.of("sh", "-c", "echo hello"),
                "slow", List.of("sh", "-c", "sleep 5"),
                "fail", List.of("sh", "-c", "exit 3"),
                "unsafe", List.of("sh", "-c", "echo {value}")));
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        var registry = new ExecutionRegistry(properties, mapper);
        var logStore = new LogStore(properties);
        var guard = new WorkspacePathGuard(properties);
        var templates = new CommandTemplates(properties);
        var orcaRunner = new com.devpilot.gateway.orca.OrcaWorkerRunner(
                new com.devpilot.gateway.orca.OrcaCli(properties, mapper),
                new com.devpilot.gateway.orca.WorkerSpecFactory(),
                new com.devpilot.gateway.orca.WorkspaceInspector(),
                new com.devpilot.gateway.orca.StructuredOutputParser(mapper),
                registry, properties, mapper);
        service = new ExecutionService(registry, logStore, guard, templates,
                new SystemProcessLauncher(), properties, mapper, orcaRunner);
    }

    @Test
    void successfulCommandProducesLogsAndZeroExit() throws Exception {
        var request = command("key-1", "quick", Duration.ofSeconds(3));
        var submit = service.submitCommand(request);
        assertThat(submit.executionId()).isNotBlank();

        waitForTerminal(submit.executionId(), Duration.ofSeconds(5));
        var state = service.status(submit.executionId());

        assertThat(state.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(state.exitCode()).isEqualTo(0);
        assertThat(service.logs(submit.executionId(), null).lines())
                .anyMatch(line -> line.text().contains("hello"));
    }

    @Test
    void duplicateKeyReturnsSameExecutionAndDoesNotRelaunch() throws Exception {
        var request = command("key-dup", "quick", Duration.ofSeconds(3));

        var first = service.submitCommand(request);
        waitForTerminal(first.executionId(), Duration.ofSeconds(5));
        var second = service.submitCommand(request);

        assertThat(second.executionId()).isEqualTo(first.executionId());
        assertThat(second.idempotentReplay()).isTrue();
    }

    @Test
    void failingCommandIsReportedAsFailed() throws Exception {
        var submit = service.submitCommand(command("key-fail", "fail", Duration.ofSeconds(3)));
        waitForTerminal(submit.executionId(), Duration.ofSeconds(5));

        var state = service.status(submit.executionId());
        assertThat(state.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(state.exitCode()).isEqualTo(3);
    }

    @Test
    void failedExecutionCanBeRetriedUnderSameKey() throws Exception {
        var failed = service.submitCommand(command("key-retry", "fail", Duration.ofSeconds(3)));
        waitForTerminal(failed.executionId(), Duration.ofSeconds(5));
        assertThat(service.status(failed.executionId()).status()).isEqualTo(ExecutionStatus.FAILED);

        var retried = service.submitCommand(command("key-retry", "quick", Duration.ofSeconds(3)));

        assertThat(retried.executionId()).isNotEqualTo(failed.executionId());
        assertThat(retried.idempotentReplay()).isFalse();
        waitForTerminal(retried.executionId(), Duration.ofSeconds(5));
        assertThat(service.status(retried.executionId()).status()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    @Test
    void timeoutMarksExecutionTimedOut() throws Exception {
        var submit = service.submitCommand(command("key-slow", "slow", Duration.ofMillis(300)));
        waitForTerminal(submit.executionId(), Duration.ofSeconds(5));

        assertThat(service.status(submit.executionId()).status())
                .isEqualTo(ExecutionStatus.TIMED_OUT);
    }

    @Test
    void cancelMarksExecutionCancelled() throws Exception {
        var submit = service.submitCommand(command("key-cancel", "slow", Duration.ofSeconds(5)));
        Thread.sleep(200);
        service.cancel(submit.executionId());

        assertThat(service.status(submit.executionId()).status())
                .isEqualTo(ExecutionStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(submit.executionId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unsafeParameterIsRejectedWithoutLaunching() {
        var request = new NodeCommandRequest("key-unsafe", "task-1", "unsafe", "task-1",
                Map.of("value", "x; touch pwned"), Duration.ofSeconds(2));
        var result = service.submitCommand(request);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(service.status(result.executionId()).message()).contains("rejected");
    }

    @Test
    void disabledGatewayRefusesToLaunch() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.submitCommand(command("key-off", "quick", Duration.ofSeconds(2))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("disabled");
    }

    private NodeCommandRequest command(String key, String commandKey, Duration timeout) {
        return new NodeCommandRequest(key, "task-1", commandKey, "task-1", Map.of(), timeout);
    }

    private void waitForTerminal(String executionId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            var status = service.status(executionId).status();
            if (status == ExecutionStatus.SUCCEEDED || status == ExecutionStatus.FAILED
                    || status == ExecutionStatus.CANCELLED || status == ExecutionStatus.TIMED_OUT) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("execution did not finish within " + timeout);
    }
}
