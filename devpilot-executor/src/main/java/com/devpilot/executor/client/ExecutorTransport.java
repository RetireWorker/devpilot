package com.devpilot.executor.client;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.NodeCommandRequest;

/**
 * Thin transport boundary. Local and remote differ only in endpoint and
 * credentials, so swapping the topology never changes business code.
 */
public interface ExecutorTransport {

    ExecutionSubmitResult submitExecution(AgentExecutionRequest request);

    ExecutionState status(String executionId);

    ExecutionLogPage logs(String executionId, String cursor);

    AgentExecutionResult result(String executionId);

    void cancel(String executionId);

    ExecutionSubmitResult submitCommand(NodeCommandRequest request);

    ExecutionState commandStatus(String executionId);

    ExecutionLogPage commandLogs(String executionId, String cursor);

    AgentExecutionResult commandResult(String executionId);

    void commandCancel(String executionId);

    ExecutorHealth health();
}
