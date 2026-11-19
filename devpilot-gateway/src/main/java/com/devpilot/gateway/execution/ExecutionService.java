package com.devpilot.gateway.execution;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.domain.ExecutorMode;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import com.devpilot.gateway.orca.OrcaWorkerRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRegistry registry;
    private final LogStore logStore;
    private final WorkspacePathGuard pathGuard;
    private final CommandTemplates templates;
    private final ProcessLauncher launcher;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final OrcaWorkerRunner orcaWorkerRunner;
    private final Map<String, RunningProcess> running = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService workerPool;

    public ExecutionService(ExecutionRegistry registry,
                            LogStore logStore,
                            WorkspacePathGuard pathGuard,
                            CommandTemplates templates,
                            ProcessLauncher launcher,
                            GatewayProperties properties,
                            ObjectMapper objectMapper,
                            OrcaWorkerRunner orcaWorkerRunner) {
        this.registry = registry;
        this.logStore = logStore;
        this.pathGuard = pathGuard;
        this.templates = templates;
        this.launcher = launcher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.orcaWorkerRunner = orcaWorkerRunner;
        this.workerPool = Executors.newFixedThreadPool(
                Math.max(1, properties.getMaxConcurrentWorkers()), runnable -> {
                    Thread thread = new Thread(runnable, "devpilot-orca-worker");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public ExecutionSubmitResult submitAgent(AgentExecutionRequest request) {
        if (request.executionKey() == null || request.executionKey().isBlank()) {
            throw new ValidationException("executionKey is required for idempotency");
        }
        if (request.role() == null) {
            throw new ValidationException("agent role is required");
        }
        if ("orca".equalsIgnoreCase(properties.getAgentRunner())) {
            return submitOrcaWorker(request);
        }
        Map<String, String> parameters = new HashMap<>();
        if (request.metadata() != null) {
            parameters.putAll(request.metadata());
        }
        parameters.put("taskId", request.taskId());
        if (request.subtaskId() != null) {
            parameters.put("subtaskId", request.subtaskId());
        }
        if (request.workspaceRelativePath() != null) {
            parameters.put("workspace", request.workspaceRelativePath());
        }
        List<String> argv;
        try {
            argv = templates.agent(request.role(), parameters);
        } catch (ValidationException e) {
            return rejected(request.executionKey(), request.taskId(), request.subtaskId(),
                    request.projectId(), request.workspaceId(), request.workspaceRelativePath(),
                    "agent", request.role().name(), e.getMessage());
        }
        return submitInternal(request.executionKey(), request.taskId(), request.subtaskId(),
                request.projectId(), request.workspaceId(), request.workspaceRelativePath(),
                "agent", request.role().name(), argv, request.timeout());
    }

    public ExecutionSubmitResult submitCommand(NodeCommandRequest request) {
        if (request.executionKey() == null || request.executionKey().isBlank()) {
            throw new ValidationException("executionKey is required for idempotency");
        }
        List<String> argv;
        try {
            argv = templates.command(request.commandKey(), request.parameters());
        } catch (ValidationException e) {
            return rejected(request.executionKey(), request.taskId(), null, null, null,
                    request.relativeWorkspace(), "command", request.commandKey(), e.getMessage());
        }
        return submitInternal(request.executionKey(), request.taskId(), null, null, null,
                request.relativeWorkspace(), "command", request.commandKey(), argv, request.timeout());
    }

    /**
     * Runs the task as a supervised Orca worker. The worker lives outside this
     * process, so the timeout callback stops it explicitly instead of killing a
     * local child process.
     */
    private ExecutionSubmitResult submitOrcaWorker(AgentExecutionRequest request) {
        ExecutionSubmitResult replay = replayable(request.executionKey());
        if (replay != null) {
            return replay;
        }
        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "gateway execution is disabled; set ai.gateway.enabled=true only on an authorized node");
        }
        Path workspace = pathGuard.resolve(request.workspaceRelativePath());

        var record = new ExecutionRecord();
        record.setExecutionId("EX-" + UUID.randomUUID());
        record.setExecutionKey(request.executionKey());
        record.setTaskId(request.taskId());
        record.setSubtaskId(request.subtaskId());
        record.setProjectId(request.projectId());
        record.setWorkspaceId(request.workspaceId());
        record.setRelativeWorkspace(request.workspaceRelativePath());
        record.setKind("agent");
        record.setCommandKey(request.role().name());
        record.setStatus(ExecutionStatus.PENDING);
        record.setCreatedAt(Instant.now());
        record.setLogFile(logStore.newLogFile(record.getExecutionId()).toString());
        registry.register(record);

        Duration timeout = effectiveTimeout(request.timeout());
        scheduler.schedule(() -> {
            orcaWorkerRunner.requestCancel(record.getExecutionId(), record);
            timeout(record);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        workerPool.execute(() -> orcaWorkerRunner.run(record, request, workspace, timeout));

        return new ExecutionSubmitResult(record.getExecutionId(), record.getExecutionKey(),
                record.getStatus(), record.getCreatedAt(), false);
    }

    private Duration effectiveTimeout(Duration requested) {
        return requested == null || requested.isZero() ? properties.getDefaultTimeout() : requested;
    }

    private ExecutionSubmitResult replayable(String executionKey) {
        return registry.idempotentExecutionId(executionKey)
                .map(registry::require)
                .filter(record -> record.getStatus() == ExecutionStatus.PENDING
                        || record.getStatus() == ExecutionStatus.RUNNING
                        || record.getStatus() == ExecutionStatus.SUCCEEDED)
                .map(record -> new ExecutionSubmitResult(record.getExecutionId(),
                        record.getExecutionKey(), record.getStatus(),
                        record.getCreatedAt(), true))
                .orElse(null);
    }

    private ExecutionSubmitResult submitInternal(String executionKey, String taskId, String subtaskId,
                                                 String projectId, String workspaceId,
                                                 String relativeWorkspace, String kind,
                                                 String commandKey, List<String> argv,
                                                 Duration requestedTimeout) {
        ExecutionSubmitResult replay = replayable(executionKey);
        if (replay != null) {
            return replay;
        }
        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "gateway execution is disabled; set ai.gateway.enabled=true only on an authorized node");
        }
        Path workspace = pathGuard.resolve(relativeWorkspace);

        var record = new ExecutionRecord();
        record.setExecutionId("EX-" + UUID.randomUUID());
        record.setExecutionKey(executionKey);
        record.setTaskId(taskId);
        record.setSubtaskId(subtaskId);
        record.setProjectId(projectId);
        record.setWorkspaceId(workspaceId);
        record.setRelativeWorkspace(relativeWorkspace);
        record.setKind(kind);
        record.setCommandKey(commandKey);
        record.setStatus(ExecutionStatus.PENDING);
        record.setCreatedAt(Instant.now());
        record.setLogFile(logStore.newLogFile(record.getExecutionId()).toString());
        registry.register(record);

        Duration timeout = requestedTimeout == null || requestedTimeout.isZero()
                ? properties.getDefaultTimeout() : requestedTimeout;
        var cancelled = new AtomicBoolean(false);
        var processHolder = new java.util.concurrent.atomic.AtomicReference<ProcessLauncher.ProcessHandle>();
        scheduler.execute(() -> run(record, argv, workspace, timeout, cancelled, processHolder));
        scheduler.schedule(() -> {
            if (cancelled.compareAndSet(false, true)) {
                var handle = processHolder.get();
                if (handle != null) {
                    handle.destroy();
                }
                timeout(record);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return new ExecutionSubmitResult(record.getExecutionId(), record.getExecutionKey(),
                record.getStatus(), record.getCreatedAt(), false);
    }

    private void run(ExecutionRecord record, List<String> argv, Path workspace, Duration timeout,
                     AtomicBoolean cancelled,
                     java.util.concurrent.atomic.AtomicReference<ProcessLauncher.ProcessHandle> holder) {
        record.setStatus(ExecutionStatus.RUNNING);
        record.setStartedAt(Instant.now());
        registry.update(record);
        Path logFile = Path.of(record.getLogFile());
        try {
            Files.createDirectories(logFile.getParent());
            ProcessLauncher.ProcessHandle handle = launcher.launch(argv, workspace, logFile, logFile);
            holder.set(handle);
            running.put(record.getExecutionId(), new RunningProcess(handle, cancelled));
            boolean exited = handle.waitFor(timeout.toMillis());
            if (!exited) {
                cancelled.set(true);
                handle.destroyForcibly();
                record.setExitCode(handle.exitCode());
                timeout(record);
                return;
            }
            if (record.getStatus() == ExecutionStatus.TIMED_OUT) {
                return;
            }
            if (cancelled.get()) {
                record.setStatus(ExecutionStatus.CANCELLED);
                record.setFinishedAt(Instant.now());
                record.setMessage("execution cancelled");
            } else {
                int exitCode = handle.exitCode();
                record.setExitCode(exitCode);
                record.setStatus(exitCode == 0 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED);
                record.setFinishedAt(Instant.now());
                record.setMessage(exitCode == 0 ? "command completed" : "command failed with exit code " + exitCode);
                record.setSummary(exitCode == 0 ? "OK" : "FAILED");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record.setStatus(ExecutionStatus.CANCELLED);
            record.setMessage("interrupted");
        } catch (IOException e) {
            record.setStatus(ExecutionStatus.FAILED);
            record.setFinishedAt(Instant.now());
            record.setMessage("cannot launch process: " + e.getMessage());
        } finally {
            running.remove(record.getExecutionId());
            logStore.trim(logFile);
            if (record.getStatus() != ExecutionStatus.TIMED_OUT
                    && record.getStatus() != ExecutionStatus.CANCELLED) {
                registry.update(record);
            }
        }
    }

    private void timeout(ExecutionRecord record) {
        boolean first = record.getStatus() == ExecutionStatus.PENDING
                || record.getStatus() == ExecutionStatus.RUNNING;
        if (!first) {
            return;
        }
        record.setStatus(ExecutionStatus.TIMED_OUT);
        record.setFinishedAt(Instant.now());
        record.setMessage("execution timed out");
        registry.update(record);
    }

    private ExecutionSubmitResult rejected(String executionKey, String taskId, String subtaskId,
                                           String projectId, String workspaceId,
                                           String relativeWorkspace, String kind,
                                           String commandKey, String message) {
        var record = new ExecutionRecord();
        record.setExecutionId("EX-" + UUID.randomUUID());
        record.setExecutionKey(executionKey);
        record.setTaskId(taskId);
        record.setSubtaskId(subtaskId);
        record.setProjectId(projectId);
        record.setWorkspaceId(workspaceId);
        record.setRelativeWorkspace(relativeWorkspace);
        record.setKind(kind);
        record.setCommandKey(commandKey);
        record.setStatus(ExecutionStatus.FAILED);
        record.setCreatedAt(Instant.now());
        record.setFinishedAt(Instant.now());
        record.setMessage("rejected: " + message);
        record.setLogFile(logStore.newLogFile(record.getExecutionId()).toString());
        registry.register(record);
        return new ExecutionSubmitResult(record.getExecutionId(), executionKey,
                ExecutionStatus.FAILED, record.getCreatedAt(), false);
    }

    public ExecutionState status(String executionId) {
        ExecutionRecord record = registry.require(executionId);
        return new ExecutionState(record.getExecutionId(), record.getStatus(), record.getExitCode(),
                record.getStartedAt(), record.getFinishedAt(), record.getMessage());
    }

    public ExecutionLogPage logs(String executionId, String cursor) {
        registry.require(executionId);
        return logStore.page(executionId, cursor);
    }

    public AgentExecutionResult result(String executionId) {
        ExecutionRecord record = registry.require(executionId);
        Map<String, Object> output = Map.of();
        if (record.getResultJson() != null && !record.getResultJson().isBlank()) {
            try {
                output = objectMapper.readValue(record.getResultJson(), Map.class);
            } catch (IOException ignored) {
                output = Map.of("unparseableResult", record.getResultJson());
            }
        }
        return new AgentExecutionResult(record.getExecutionId(), record.getStatus(),
                record.getSummary() == null ? record.getMessage() : record.getSummary(),
                stringList(output.get("changedFiles")),
                output.get("commitSha") instanceof String sha ? sha : null,
                output, stringList(output.get("violations")), record.getFinishedAt());
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }

    public void cancel(String executionId) {
        ExecutionRecord record = registry.require(executionId);
        if (record.getStatus() == ExecutionStatus.SUCCEEDED
                || record.getStatus() == ExecutionStatus.FAILED
                || record.getStatus() == ExecutionStatus.CANCELLED
                || record.getStatus() == ExecutionStatus.TIMED_OUT) {
            throw new ConflictException("execution already finished: " + record.getStatus());
        }
        orcaWorkerRunner.requestCancel(executionId, record);
        RunningProcess process = running.get(executionId);
        if (process != null) {
            process.cancelled.set(true);
            process.handle.destroy();
        }
        record.setStatus(ExecutionStatus.CANCELLED);
        record.setFinishedAt(Instant.now());
        record.setMessage("cancel requested");
        registry.update(record);
    }

    public ExecutorHealth health() {
        boolean healthy = properties.isEnabled();
        String detail = healthy
                ? "gateway enabled, runner=" + properties.getAgentRunner()
                + ", agent=" + properties.getDefaultAgentId()
                + ", " + registry.all().size() + " executions tracked"
                : "gateway disabled: no execution will be launched";
        return new ExecutorHealth(healthy, "orca-gateway", ExecutorMode.LOCAL, detail, Instant.now());
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(process -> process.handle.destroyForcibly());
        scheduler.shutdownNow();
        workerPool.shutdownNow();
    }

    private static final class RunningProcess {
        private final ProcessLauncher.ProcessHandle handle;
        private final AtomicBoolean cancelled;

        private RunningProcess(ProcessLauncher.ProcessHandle handle, AtomicBoolean cancelled) {
            this.handle = handle;
            this.cancelled = cancelled;
        }
    }
}
