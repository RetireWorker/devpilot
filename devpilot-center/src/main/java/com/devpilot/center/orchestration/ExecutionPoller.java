package com.devpilot.center.orchestration;

import com.devpilot.center.config.DevpilotProperties;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.NodeCommandExecutor;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.error.DevpilotException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Waits outside transaction boundaries so a long agent run never holds a DB
 * connection. Polling is deliberately simple and restart-safe: the center can
 * re-observe an execution by id after a crash.
 */
@Component
public class ExecutionPoller {

    private final AgentExecutor executor;
    private final NodeCommandExecutor commandExecutor;
    private final DevpilotProperties properties;

    public ExecutionPoller(AgentExecutor executor, NodeCommandExecutor commandExecutor,
                           DevpilotProperties properties) {
        this.executor = executor;
        this.commandExecutor = commandExecutor;
        this.properties = properties;
    }

    public AgentExecutionResult await(String executionId) {
        Instant deadline = Instant.now().plus(properties.getExecutionTimeout());
        while (Instant.now().isBefore(deadline)) {
            AgentExecutionResult result = executor.getResult(executionId);
            if (!isRunning(result.status())) {
                return result;
            }
            sleep();
        }
        executor.cancel(executionId);
        throw new DevpilotException("execution " + executionId + " exceeded orchestration timeout");
    }

    public ExecutionState awaitCommand(String executionId) {
        Instant deadline = Instant.now().plus(properties.getExecutionTimeout());
        while (Instant.now().isBefore(deadline)) {
            ExecutionState state = commandExecutor.getCommandStatus(executionId);
            if (!isRunning(state.status())) {
                return state;
            }
            sleep();
        }
        commandExecutor.cancelCommand(executionId);
        throw new DevpilotException("command " + executionId + " exceeded orchestration timeout");
    }

    private boolean isRunning(ExecutionStatus status) {
        return status == ExecutionStatus.PENDING || status == ExecutionStatus.RUNNING;
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getExecutionPollInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DevpilotException("interrupted while waiting for execution", e);
        }
    }
}
