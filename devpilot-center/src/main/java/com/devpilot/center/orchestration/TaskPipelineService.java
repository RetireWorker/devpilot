package com.devpilot.center.orchestration;

import com.devpilot.center.audit.AuditService;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.api.ProjectCatalog;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.TestRunResult;
import com.devpilot.common.api.VerificationResultView;
import com.devpilot.common.domain.TaskState;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.task.service.TaskService;
import com.devpilot.task.service.TaskStatusView;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Drives one stage of a task. The state machine is the gate: calling a stage in
 * the wrong state fails instead of silently doing work.
 */
@Service
public class TaskPipelineService {

    private final TaskService taskService;
    private final ProjectCatalog projectCatalog;
    private final VerificationRunner verificationRunner;
    private final AuditService auditService;

    public TaskPipelineService(TaskService taskService,
                               ProjectCatalog projectCatalog,
                               VerificationRunner verificationRunner,
                               AuditService auditService) {
        this.taskService = taskService;
        this.projectCatalog = projectCatalog;
        this.verificationRunner = verificationRunner;
        this.auditService = auditService;
    }

    public DevelopOutcome develop(String taskId, String actor) {
        TaskStatusView task = requireState(taskId, TaskState.DEVELOPING);
        ProjectDescriptor project = requireProject(task.projectId());

        AgentExecutionResult result = verificationRunner.runDeveloper(task, project,
                "修复 BUG 并补充回归测试；禁止删除/跳过测试或弱化断言");

        if (result.status() != ExecutionStatus.SUCCEEDED
                || (result.violations() != null && !result.violations().isEmpty())) {
            auditService.record(actor, "DEVELOPMENT_FAILED", "TASK", taskId,
                    String.valueOf(result.summary()));
            return new DevelopOutcome(taskId, false, result.status().name(),
                    result.summary(), List.of());
        }

        CodeChangeView change = toChange(task, project, result);
        taskService.recordCommit(taskId, change, actor);
        auditService.record(actor, "DEVELOPMENT_COMMITTED", "TASK", taskId,
                String.valueOf(result.commitSha()));
        return new DevelopOutcome(taskId, true, result.status().name(), result.summary(),
                change.files().stream().map(CodeChangeView.ChangedFile::path).toList());
    }

    public VerifyOutcome verify(String taskId, String actor) {
        TaskStatusView task = requireState(taskId, TaskState.TESTING);
        ProjectDescriptor project = requireProject(task.projectId());

        TestRunResult tests = verificationRunner.runTests(task, project);
        taskService.recordTestResult(taskId, tests.passed(), tests.exitCode(),
                tests.reportLocation(), describe(tests), actor);
        if (!tests.passed()) {
            auditService.record(actor, "TEST_FAILED", "TASK", taskId, tests.command());
            return new VerifyOutcome(taskId, false, TaskState.DEVELOPING.name(),
                    "TEST_FAILED", tests.failureSummary(), null);
        }

        TaskStatusView reviewing = taskService.status(taskId);
        CodeChangeView change = latestChange(reviewing)
                .orElseThrow(() -> new ConflictException("缺少代码变更记录，无法审查"));
        VerificationResultView review = verificationRunner.review(reviewing, project, change);
        taskService.recordReview(taskId, review, actor);
        TaskStatusView after = taskService.status(taskId);
        auditService.record(actor, review.passed() ? "REVIEW_PASSED" : "REVIEW_FAILED",
                "TASK", taskId, review.summary());
        return new VerifyOutcome(taskId, review.passed(), after.state().name(),
                review.passed() ? "REVIEW_PASSED" : "REVIEW_FAILED",
                tests.failureSummary(), review);
    }

    private java.util.Optional<CodeChangeView> latestChange(TaskStatusView task) {
        var report = taskService.report(task.taskId());
        return java.util.Optional.ofNullable(report.codeChange());
    }

    private CodeChangeView toChange(TaskStatusView task, ProjectDescriptor project,
                                    AgentExecutionResult result) {
        List<CodeChangeView.ChangedFile> files = result.changedFiles() == null
                ? List.of()
                : result.changedFiles().stream()
                .map(path -> new CodeChangeView.ChangedFile(path, "MODIFIED", 0, 0))
                .toList();
        return new CodeChangeView(task.taskId(), task.workspaceId(), task.branch(),
                result.commitSha(), project.defaultBranch(), files, 0, 0);
    }

    private String describe(TestRunResult tests) {
        return tests.passed() ? "测试通过，退出码 0"
                : "测试失败，退出码 " + tests.exitCode() + ": "
                + String.join("; ", tests.failureSummary());
    }

    private TaskStatusView requireState(String taskId, TaskState expected) {
        TaskStatusView task = taskService.status(taskId);
        if (task.state() != expected) {
            throw new ConflictException("任务当前状态为 " + task.state()
                    + "，期望 " + expected);
        }
        return task;
    }

    private ProjectDescriptor requireProject(String projectId) {
        return projectCatalog.findById(projectId)
                .orElseThrow(() -> new ValidationException("项目未注册: " + projectId));
    }

    public record DevelopOutcome(String taskId, boolean succeeded, String executionStatus,
                                 String summary, List<String> changedFiles) {
    }

    public record VerifyOutcome(String taskId, boolean passed, String taskState, String outcome,
                                List<String> failures, VerificationResultView review) {
    }
}
