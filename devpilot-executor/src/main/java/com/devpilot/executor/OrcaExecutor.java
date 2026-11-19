package com.devpilot.executor;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.NodeCommandExecutor;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.domain.ExecutorMode;
import com.devpilot.executor.client.ExecutorTransport;

import java.time.Instant;

public class OrcaExecutor extends TransportBackedExecutor implements AgentExecutor, NodeCommandExecutor {

    private final ExecutorMode mode;

    public OrcaExecutor(ExecutorTransport transport, String executorId, ExecutorMode mode) {
        super(transport, executorId);
        this.mode = mode;
    }

    @Override
    public ExecutionSubmitResult submit(AgentExecutionRequest request) {
        return transport().submitExecution(request);
    }

    @Override
    public ExecutionState getStatus(String executionId) {
        return transport().status(executionId);
    }

    @Override
    public ExecutionLogPage getLogs(String executionId, String cursor) {
        return transport().logs(executionId, cursor);
    }

    @Override
    public AgentExecutionResult getResult(String executionId) {
        return transport().result(executionId);
    }

    @Override
    public void cancel(String executionId) {
        transport().cancel(executionId);
    }

    @Override
    public ExecutionSubmitResult submitCommand(NodeCommandRequest request) {
        return transport().submitCommand(request);
    }

    @Override
    public ExecutionState getCommandStatus(String executionId) {
        return transport().commandStatus(executionId);
    }

    @Override
    public ExecutionLogPage getCommandLogs(String executionId, String cursor) {
        return transport().commandLogs(executionId, cursor);
    }

    @Override
    public AgentExecutionResult getCommandResult(String executionId) {
        return transport().commandResult(executionId);
    }

    @Override
    public void cancelCommand(String executionId) {
        transport().commandCancel(executionId);
    }

    @Override
    public ExecutorHealth health() {
        try {
            ExecutorHealth remote = transport().health();
            return new ExecutorHealth(remote.healthy(), executorId(), mode, remote.detail(), Instant.now());
        } catch (RuntimeException e) {
            return new ExecutorHealth(false, executorId(), mode, e.getMessage(), Instant.now());
        }
    }
}
