package com.devpilot.gateway.orca;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentRole;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import com.devpilot.gateway.execution.ExecutionRecord;
import com.devpilot.gateway.execution.ExecutionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes one Agent task as a supervised Orca worker and turns the worker
 * lifecycle into the gateway's execution status.
 *
 * <p>The agent's own claim is never trusted: tests and changed files come from
 * the real workspace and the deterministic inspection, and a scope violation
 * fails the execution instead of producing a commit.
 */
@Component
public class OrcaWorkerRunner {

    private static final Logger log = LoggerFactory.getLogger(OrcaWorkerRunner.class);

    private static final Set<String> SUCCESS_STATES = Set.of("succeeded", "completed", "done");
    private static final Set<String> TERMINAL_OUTCOMES = Set.of(
            "succeeded", "failed", "cancelled", "stopped");

    private final OrcaCli cli;
    private final WorkerSpecFactory specFactory;
    private final WorkspaceInspector inspector;
    private final StructuredOutputParser outputParser;
    private final ExecutionRegistry registry;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final Map<String, String> activeDispatches = new ConcurrentHashMap<>();

    public OrcaWorkerRunner(OrcaCli cli, WorkerSpecFactory specFactory,
                            WorkspaceInspector inspector, StructuredOutputParser outputParser,
                            ExecutionRegistry registry, GatewayProperties properties,
                            ObjectMapper objectMapper) {
        this.cli = cli;
        this.specFactory = specFactory;
        this.inspector = inspector;
        this.outputParser = outputParser;
        this.registry = registry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void run(ExecutionRecord record, AgentExecutionRequest request, Path workspace,
                    Duration timeout) {
        String dispatchId = null;
        String cursor = null;
        List<OrcaCli.Message> transcript = new ArrayList<>();
        String baseSha = inspector.head(workspace);
        try {
            record.setStatus(ExecutionStatus.RUNNING);
            record.setStartedAt(Instant.now());
            registry.update(record);
            append(record, "# devpilot worker role=" + request.role()
                    + " workspace=" + request.workspaceRelativePath() + "\n");

            Path specFile = writeSpec(record, request);
            OrcaCli.WorkerStart start = cli.startWorker(workspace, specFile,
                    agentId(request.role()),
                    abbreviate(request.taskId() + " " + request.role(), 120),
                    properties.getWorkerModel());
            dispatchId = start.dispatchId();
            record.setDispatchId(dispatchId);
            registry.update(record);
            activeDispatches.put(record.getExecutionId(), dispatchId);
            append(record, "# orca dispatch=" + dispatchId + " state=" + start.state() + "\n");

            Instant deadline = Instant.now().plus(timeout);
            Instant idleSince = null;
            var messageIndex = new java.util.HashMap<String, Integer>();
            while (true) {
                if (record.getStatus() != ExecutionStatus.RUNNING
                        || cancelled.contains(record.getExecutionId())) {
                    cli.stop(dispatchId);
                    return;
                }
                OrcaCli.WorkerRead page = readHandlingSourceChange(dispatchId, cursor);
                cursor = page.cursor();
                mergeMessages(record, transcript, messageIndex, page.messages());
                OrcaCli.WorkerView view = cli.show(dispatchId);
                boolean terminal = view.terminal()
                        || TERMINAL_OUTCOMES.contains(String.valueOf(page.outcome()));
                boolean idleComplete = false;
                if (!terminal && "idle".equals(view.activity()) && !page.requiresAction()
                        && outputParser.lastJsonBlock(transcript).isPresent()) {
                    // A structured chat worker can stay open after finishing its
                    // turn; a stable idle with the final JSON means it is done.
                    if (idleSince == null) {
                        idleSince = Instant.now();
                    } else if (Duration.between(idleSince, Instant.now())
                            .compareTo(properties.getWorkerIdleSettle()) >= 0) {
                        idleComplete = true;
                    }
                } else {
                    idleSince = null;
                }
                if (terminal || idleComplete) {
                    // The worker can settle a moment before its final answer lands
                    // in the transcript; drain briefly so structured output is not lost.
                    OrcaCli.WorkerRead finalPage = drainFinal(record, dispatchId, cursor,
                            transcript, messageIndex, properties.getWorkerDrainWindow());
                    if (idleComplete && !terminal) {
                        cli.stop(dispatchId);
                    }
                    finalizeRun(record, request, workspace, baseSha, view, finalPage, transcript,
                            idleComplete ? Boolean.TRUE : null);
                    return;
                }
                if (Instant.now().isAfter(deadline)) {
                    cli.stop(dispatchId);
                    markTimedOut(record, "agent exceeded " + timeout);
                    return;
                }
                Thread.sleep(Math.max(500, properties.getWorkerPollInterval().toMillis()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(record, "interrupted while waiting for the worker");
        } catch (ValidationException e) {
            markFailed(record, e.getMessage());
        } catch (RuntimeException e) {
            log.error("orca worker execution failed", e);
            markFailed(record, "worker execution failed: " + e.getMessage());
        } finally {
            activeDispatches.remove(record.getExecutionId());
            if (dispatchId != null) {
                cli.release(dispatchId);
                cli.hideWorkspace(workspace);
            }
            cancelled.remove(record.getExecutionId());
        }
    }

    public void requestCancel(String executionId, ExecutionRecord record) {
        cancelled.add(executionId);
        String dispatchId = record.getDispatchId();
        if (dispatchId == null) {
            dispatchId = activeDispatches.get(executionId);
        }
        if (dispatchId != null && !dispatchId.isBlank()) {
            cli.stop(dispatchId);
        }
    }

    @PreDestroy
    public void stopAll() {
        activeDispatches.values().forEach(cli::stop);
        activeDispatches.clear();
    }

    /**
     * Reads the remaining transcript after the worker settles. Streams for the
     * whole settle window so a final answer written just after settlement is
     * not lost.
     */
    private OrcaCli.WorkerRead drainFinal(ExecutionRecord record, String dispatchId, String cursor,
                                          List<OrcaCli.Message> transcript,
                                          Map<String, Integer> messageIndex, Duration settle)
            throws InterruptedException {
        OrcaCli.WorkerRead latest = readHandlingSourceChange(dispatchId, cursor);
        mergeMessages(record, transcript, messageIndex, latest.messages());
        Instant deadline = Instant.now().plus(settle);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(500);
            OrcaCli.WorkerRead next = readHandlingSourceChange(dispatchId, latest.cursor());
            mergeMessages(record, transcript, messageIndex, next.messages());
            latest = next;
        }
        return latest;
    }

    /**
     * Orca pins a cursor to one output source; a structured session switching
     * from terminal to transcript invalidates it, so the read restarts from the
     * beginning of the transcript (duplicates are merged by message id).
     */
    private OrcaCli.WorkerRead readHandlingSourceChange(String dispatchId, String cursor) {
        try {
            return cli.read(dispatchId, cursor, 200);
        } catch (ValidationException e) {
            if (!String.valueOf(e.getMessage()).contains("source_changed")) {
                throw e;
            }
            return cli.read(dispatchId, null, 200);
        }
    }

    /**
     * Orca may return the same transcript message while the agent is still
     * streaming it, with the text growing between reads. Keep the latest text
     * and append only the delta to the log; parsing the stale first snapshot
     * would miss the final structured answer.
     */
    private void mergeMessages(ExecutionRecord record, List<OrcaCli.Message> transcript,
                               Map<String, Integer> indexById, List<OrcaCli.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (OrcaCli.Message message : messages) {
            if (message.id() == null) {
                transcript.add(message);
                appendMessages(record, List.of(message));
                continue;
            }
            Integer index = indexById.get(message.id());
            if (index == null) {
                indexById.put(message.id(), transcript.size());
                transcript.add(message);
                appendMessages(record, List.of(message));
                continue;
            }
            OrcaCli.Message previous = transcript.get(index);
            if (previous.text().equals(message.text())) {
                continue;
            }
            transcript.set(index, message);
            String delta = message.text().startsWith(previous.text())
                    ? message.text().substring(previous.text().length())
                    : "\n" + message.text();
            append(record, delta);
        }
    }

    private void finalizeRun(ExecutionRecord record, AgentExecutionRequest request, Path workspace,
                             String baseSha, OrcaCli.WorkerView view, OrcaCli.WorkerRead page,
                             List<OrcaCli.Message> transcript, Boolean successOverride) {
        if (record.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }
        boolean succeeded = successOverride != null ? successOverride
                : SUCCESS_STATES.contains(String.valueOf(view.state()))
                || SUCCESS_STATES.contains(String.valueOf(page.outcome()));
        Optional<JsonNode> structured = outputParser.lastJsonBlock(transcript);
        String summary = structured.map(node -> node.path("summary").asText("")).orElse("");
        if (summary.isBlank()) {
            summary = succeeded ? "completed by " + agentId(request.role())
                    : "worker " + view.state() + ": " + String.join(",", page.attention());
        }

        List<String> changedFiles = inspector.changedFiles(workspace, baseSha);
        List<String> violations = inspector.violations(changedFiles,
                request.allowedPaths(), request.forbiddenPaths());
        String commitSha = null;

        if (succeeded && !violations.isEmpty()) {
            summary = "scope violation: " + String.join("; ", violations);
            succeeded = false;
        }
        if (succeeded && properties.isAutoCommit() && !changedFiles.isEmpty()) {
            try {
                commitSha = inspector.commit(workspace, commitMessage(request.taskId(), summary),
                        properties.getCommitAuthorName(), properties.getCommitAuthorEmail());
                changedFiles = inspector.changedFiles(workspace, baseSha);
            } catch (ValidationException e) {
                summary = "cannot commit agent change: " + e.getMessage();
                succeeded = false;
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("exitCode", succeeded ? 0 : 1);
        output.put("workerState", view.state());
        output.put("dispatchId", record.getDispatchId());
        output.put("summary", summary);
        output.put("changedFiles", changedFiles);
        output.put("violations", violations);
        if (commitSha != null) {
            output.put("commitSha", commitSha);
        }
        structured.ifPresent(node -> {
            if (node.hasNonNull("review")) {
                output.put("review", node.get("review").toString());
            }
            output.put("structured", objectMapper.convertValue(node, Map.class));
        });

        record.setExitCode(succeeded ? 0 : 1);
        record.setSummary(summary);
        record.setResultJson(writeJson(output));
        record.setStatus(succeeded ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED);
        record.setFinishedAt(Instant.now());
        record.setMessage(succeeded ? "worker settled" : "worker failed: " + view.state());
        registry.update(record);
    }

    private Path writeSpec(ExecutionRecord record, AgentExecutionRequest request) {
        Path specDir = Paths.get(properties.getStateDir()).toAbsolutePath().resolve("specs");
        try {
            Files.createDirectories(specDir);
            Path specFile = specDir.resolve(record.getExecutionId() + ".md");
            Files.writeString(specFile, specFactory.compose(request), StandardCharsets.UTF_8);
            return specFile;
        } catch (IOException e) {
            throw new ValidationException("cannot write worker spec: " + e.getMessage());
        }
    }

    private void appendMessages(ExecutionRecord record, List<OrcaCli.Message> messages) {
        StringBuilder block = new StringBuilder();
        for (OrcaCli.Message message : messages) {
            block.append('\n').append('[').append(message.role()).append("] ")
                    .append(message.text()).append('\n');
        }
        append(record, block.toString());
    }

    private void append(ExecutionRecord record, String text) {
        String logFile = record.getLogFile();
        if (logFile == null || text == null || text.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(logFile);
            Files.createDirectories(path.getParent());
            long size = Files.exists(path) ? Files.size(path) : 0L;
            if (size >= properties.getMaxLogBytes()) {
                return;
            }
            Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.debug("cannot append worker log: {}", e.getMessage());
        }
    }

    private void markFailed(ExecutionRecord record, String summary) {
        if (record.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }
        record.setStatus(ExecutionStatus.FAILED);
        record.setExitCode(1);
        record.setSummary(summary);
        record.setMessage(summary);
        record.setFinishedAt(Instant.now());
        record.setResultJson(writeJson(Map.of("exitCode", 1, "summary", summary)));
        registry.update(record);
        append(record, "\n[devpilot] FAILED: " + summary + "\n");
    }

    private void markTimedOut(ExecutionRecord record, String summary) {
        if (record.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }
        record.setStatus(ExecutionStatus.TIMED_OUT);
        record.setMessage(summary);
        record.setFinishedAt(Instant.now());
        registry.update(record);
        append(record, "\n[devpilot] TIMED_OUT: " + summary + "\n");
    }

    private String agentId(AgentRole role) {
        String configured = properties.getAgentIds().get(role.name());
        if (configured == null || configured.isBlank()) {
            configured = properties.getDefaultAgentId();
        }
        if (configured == null || configured.isBlank()) {
            throw new ValidationException("no Orca agent id configured for role " + role);
        }
        return configured;
    }

    private String commitMessage(String taskId, String summary) {
        String firstLine = summary == null ? "" : summary.lines().findFirst().orElse("").strip();
        return "[" + taskId + "] " + abbreviate(firstLine, 100);
    }

    private String writeJson(Map<String, Object> output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "{\"error\":\"cannot serialize result\"}";
        }
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}