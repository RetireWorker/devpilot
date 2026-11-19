package com.devpilot.common.api;

/**
 * The only contract business code uses to run agents. Implementations decide
 * whether Orca is local, remote, or routed.
 */
public interface AgentExecutor {

    ExecutionSubmitResult submit(AgentExecutionRequest request);

    ExecutionState getStatus(String executionId);

    ExecutionLogPage getLogs(String executionId, String cursor);

    AgentExecutionResult getResult(String executionId);

    void cancel(String executionId);

    ExecutorHealth health();
}
