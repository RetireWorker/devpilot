package com.devpilot.executor;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.NodeCommandExecutor;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.domain.ExecutorMode;
import com.devpilot.common.error.ValidationException;
import com.devpilot.executor.config.ExecutorProperties;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picks a node by project routing tag. Every target is an AgentExecutor, so a
 * new node type does not change task orchestration.
 */
public class RoutingOrcaExecutor implements AgentExecutor, NodeCommandExecutor {

    private final Map<String, OrcaExecutor> targets;
    private final ExecutorProperties properties;

    public RoutingOrcaExecutor(Map<String, OrcaExecutor> targets, ExecutorProperties properties) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("routing executor needs at least one target");
        }
        this.targets = Map.copyOf(targets);
        this.properties = properties;
    }

    @Override
    public ExecutionSubmitResult submit(AgentExecutionRequest request) {
        return select(request.projectId(), request.metadata()).submit(request);
    }

    @Override
    public ExecutionSubmitResult submitCommand(NodeCommandRequest request) {
        Map<String, String> metadata = request.parameters() == null ? Map.of() : request.parameters();
        return select(null, metadata).submitCommand(request);
    }

    @Override
    public ExecutionState getStatus(String executionId) {
        return anyTarget().getStatus(executionId);
    }

    @Override
    public ExecutionLogPage getLogs(String executionId, String cursor) {
        return anyTarget().getLogs(executionId, cursor);
    }

    @Override
    public AgentExecutionResult getResult(String executionId) {
        return anyTarget().getResult(executionId);
    }

    @Override
    public void cancel(String executionId) {
        anyTarget().cancel(executionId);
    }

    @Override
    public ExecutionState getCommandStatus(String executionId) {
        return anyTarget().getCommandStatus(executionId);
    }

    @Override
    public ExecutionLogPage getCommandLogs(String executionId, String cursor) {
        return anyTarget().getCommandLogs(executionId, cursor);
    }

    @Override
    public AgentExecutionResult getCommandResult(String executionId) {
        return anyTarget().getCommandResult(executionId);
    }

    @Override
    public void cancelCommand(String executionId) {
        anyTarget().cancelCommand(executionId);
    }

    @Override
    public ExecutorHealth health() {
        var details = new LinkedHashMap<String, Boolean>();
        targets.forEach((name, executor) -> details.put(name, executor.health().healthy()));
        boolean healthy = !details.isEmpty() && details.values().stream().anyMatch(Boolean::booleanValue);
        return new ExecutorHealth(healthy, "routing-orca", ExecutorMode.ROUTING,
                details.toString(), Instant.now());
    }

    private OrcaExecutor select(String projectId, Map<String, String> metadata) {
        String tag = null;
        if (metadata != null) {
            tag = metadata.get("executor");
        }
        if (tag == null && projectId != null) {
            tag = properties.getRouting().get(projectId);
        }
        if (tag == null) {
            tag = properties.getDefaultTarget();
        }
        OrcaExecutor executor = tag == null ? null : targets.get(tag);
        if (executor == null) {
            throw new ValidationException("no executor node configured for tag: " + tag);
        }
        return executor;
    }

    private OrcaExecutor anyTarget() {
        return targets.values().iterator().next();
    }
}
