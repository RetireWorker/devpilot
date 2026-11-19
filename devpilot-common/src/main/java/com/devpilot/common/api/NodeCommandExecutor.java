package com.devpilot.common.api;

public interface NodeCommandExecutor {

    ExecutionSubmitResult submitCommand(NodeCommandRequest request);

    ExecutionState getCommandStatus(String executionId);

    ExecutionLogPage getCommandLogs(String executionId, String cursor);

    AgentExecutionResult getCommandResult(String executionId);

    void cancelCommand(String executionId);

    ExecutorHealth health();
}
