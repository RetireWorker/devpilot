package com.devpilot.center.orchestration;

import com.devpilot.center.config.DevpilotProperties;
import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.AgentRole;
import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.api.NodeCommandExecutor;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.TestRunResult;
import com.devpilot.common.api.VerificationResultView;
import com.devpilot.task.service.TaskStatusView;
import com.devpilot.verification.review.ReviewPolicy;
import com.devpilot.verification.review.ReviewerResultParser;
import com.devpilot.verification.test.TestCommandPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runs the executable checks. Test results come from the process exit code and
 * the deterministic review policy always applies, even when a reviewer model
 * claims success.
 */
@Service
public class VerificationRunner {

    private final AgentExecutor executor;
    private final NodeCommandExecutor commandExecutor;
    private final ExecutionPoller poller;
    private final ExecutionKeys keys;
    private final DevpilotProperties properties;

    public VerificationRunner(AgentExecutor executor, NodeCommandExecutor commandExecutor,
                              ExecutionPoller poller,
                              ExecutionKeys keys, DevpilotProperties properties) {
        this.executor = executor;
        this.commandExecutor = commandExecutor;
        this.poller = poller;
        this.keys = keys;
        this.properties = properties;
    }

    public AgentExecutionResult runDeveloper(TaskStatusView task, ProjectDescriptor project,
                                             String objective) {
        var request = new AgentExecutionRequest(
                keys.agent(task.taskId(), "fix-code", task.repairRound()),
                task.taskId(), "fix-code", task.projectId(), task.workspaceId(),
                task.workspaceRelativePath(), AgentRole.JAVA_DEVELOPER, objective,
                List.of("src/main/java/**", "src/test/java/**"),
                List.of(".git/**", "**/application-prod.yml", "**/secrets/**"),
                List.of("bug-analysis"),
                Map.of("attempt", String.valueOf(task.repairRound())),
                properties.getExecutionTimeout(),
                Map.of("cpu", "2", "memory", "2Gi"));
        return poller.await(executor.submit(request).executionId());
    }

    public TestRunResult runTests(TaskStatusView task, ProjectDescriptor project) {
        String commandKey = project.testCommandKey() == null || project.testCommandKey().isBlank()
                ? "maven-test" : project.testCommandKey();
        List<String> argv;
        try {
            argv = TestCommandPolicy.argv(commandKey);
        } catch (IllegalArgumentException e) {
            return new TestRunResult(false, -1, commandKey, List.of(e.getMessage()),
                    null, Instant.now(), 0);
        }
        String executionKey = keys.command(task.taskId(), commandKey, task.repairRound());
        var submitted = commandExecutor.submitCommand(new NodeCommandRequest(
                executionKey, task.taskId(), commandKey, task.workspaceRelativePath(),
                Map.of(), properties.getExecutionTimeout()));
        var state = poller.awaitCommand(submitted.executionId());
        AgentExecutionResult result = commandResult(state);
        int exitCode = exitCodeOf(result);
        boolean passed = result.status() == ExecutionStatus.SUCCEEDED && exitCode == 0;
        List<String> failures = passed ? List.of()
                : List.of(result.summary() == null ? "test process failed" : result.summary());
        return new TestRunResult(passed, exitCode, String.join(" ", argv), failures,
                null, result.finishedAt() == null ? Instant.now() : result.finishedAt(), 0);
    }

    public VerificationResultView review(TaskStatusView task, ProjectDescriptor project,
                                         CodeChangeView change) {
        var request = new AgentExecutionRequest(
                keys.agent(task.taskId(), "review-code", task.repairRound()),
                task.taskId(), "review-code", task.projectId(), task.workspaceId(),
                task.workspaceRelativePath(), AgentRole.CODE_REVIEWER,
                "独立审查本次差异：安全、兼容性、可维护性、测试充分性",
                List.of(), List.of(".git/**"), List.of("diff"),
                Map.of(), properties.getExecutionTimeout(),
                Map.of("cpu", "1", "memory", "1Gi"));
        AgentExecutionResult result = poller.await(executor.submit(request).executionId());
        String reviewerJson = result.structuredOutput() == null ? null
                : String.valueOf(result.structuredOutput().get("review"));
        VerificationResultView reviewer = ReviewerResultParser.parse(reviewerJson);
        VerificationResultView policy = ReviewPolicy.evaluate(change, List.of(), reviewer);
        if (policy.passed() && result.status() != ExecutionStatus.SUCCEEDED) {
            return new VerificationResultView("REVIEW", false,
                    List.of("审查 Agent 未成功完成: " + result.summary()),
                    policy.warnings(), policy.risks(), "reviewer did not complete");
        }
        return policy;
    }

    private int exitCodeOf(AgentExecutionResult result) {
        Object value = result.structuredOutput() == null
                ? null : result.structuredOutput().get("exitCode");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return result.status() == ExecutionStatus.SUCCEEDED ? 0 : -1;
    }

    private AgentExecutionResult commandResult(com.devpilot.common.api.ExecutionState state) {
        return new AgentExecutionResult(state.executionId(), state.status(), state.message(),
                List.of(), null,
                java.util.Map.of("exitCode", state.exitCode()), List.of(), state.finishedAt());
    }
}
