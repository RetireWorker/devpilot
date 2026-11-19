package com.devpilot.task.web;

import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.VerificationResultView;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.service.DeliveryOutcome;
import com.devpilot.task.service.PlanRequest;
import com.devpilot.task.service.TaskReportView;
import com.devpilot.task.service.TaskService;
import com.devpilot.task.service.TaskStatusView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Page<TaskEntity> list(Pageable pageable) {
        return taskService.list(pageable);
    }

    @GetMapping("/{taskId}/status")
    public TaskStatusView status(@PathVariable String taskId) {
        return taskService.status(taskId);
    }

    @GetMapping("/{taskId}/report")
    public TaskReportView report(@PathVariable String taskId) {
        return taskService.report(taskId);
    }

    @PostMapping("/{taskId}/confirm-project")
    public TaskStatusView confirmProject(@PathVariable String taskId,
                                         @Valid @RequestBody ConfirmProjectRequest request) {
        return taskService.confirmProject(taskId, request.projectId(), request.actor());
    }

    @PostMapping("/{taskId}/plan")
    public TaskStatusView plan(@PathVariable String taskId,
                               @RequestBody(required = false) PlanRequest request,
                               @RequestParam(defaultValue = "human") String actor) {
        return taskService.generatePlan(taskId, request, actor);
    }

    @PostMapping("/{taskId}/approve-plan")
    public TaskStatusView approvePlan(@PathVariable String taskId,
                                      @RequestParam(defaultValue = "human") String actor) {
        return taskService.approvePlan(taskId, actor);
    }

    @PostMapping("/{taskId}/prepare-workspace")
    public TaskStatusView prepareWorkspace(@PathVariable String taskId,
                                           @RequestParam(defaultValue = "system") String actor) {
        return taskService.prepareWorkspace(taskId, actor);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskStatusView cancel(@PathVariable String taskId,
                                 @RequestBody(required = false) CancelRequest request) {
        String actor = request == null || request.actor() == null ? "human" : request.actor();
        String reason = request == null ? "人工取消" : request.reason();
        return taskService.cancel(taskId, actor, reason);
    }

    @PostMapping("/{taskId}/approvals")
    public DeliveryOutcome approvals(@PathVariable String taskId,
                                     @Valid @RequestBody ApprovalRequest request) {
        return taskService.approveDelivery(taskId, request.type(), request.approver(), request.comment());
    }

    @PostMapping("/{taskId}/deliver/git")
    public DeliveryOutcome deliverGit(@PathVariable String taskId,
                                      @Valid @RequestBody ApprovalRequest request) {
        return taskService.approveDelivery(taskId, "GIT", request.approver(), request.comment());
    }

    @PostMapping("/{taskId}/deliver/svn-dcommit")
    public DeliveryOutcome deliverSvn(@PathVariable String taskId,
                                      @Valid @RequestBody ApprovalRequest request) {
        return taskService.approveDelivery(taskId, "SVN_DCOMMIT", request.approver(), request.comment());
    }

    @PostMapping("/{taskId}/changes")
    public TaskStatusView recordCommit(@PathVariable String taskId,
                                       @Valid @RequestBody CodeChangeView change) {
        return taskService.recordCommit(taskId, change, "agent");
    }

    @PostMapping("/{taskId}/test-results")
    public TaskStatusView recordTest(@PathVariable String taskId,
                                     @Valid @RequestBody TestResultRequest request) {
        return taskService.recordTestResult(taskId, request.passed(), request.exitCode(),
                request.reportRef(), request.summary(), "agent");
    }

    @PostMapping("/{taskId}/reviews")
    public TaskStatusView recordReview(@PathVariable String taskId,
                                       @Valid @RequestBody VerificationResultView review) {
        return taskService.recordReview(taskId, review, "reviewer");
    }

    public record ConfirmProjectRequest(@NotBlank String projectId, String actor) {
    }

    public record CancelRequest(String actor, String reason) {
    }

    public record ApprovalRequest(@NotBlank String approver,
                                  String type,
                                  String comment) {
    }

    public record TestResultRequest(boolean passed, Integer exitCode,
                                    String reportRef, String summary) {
    }
}
