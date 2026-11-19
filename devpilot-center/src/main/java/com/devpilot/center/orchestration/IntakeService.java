package com.devpilot.center.orchestration;

import com.devpilot.bug.BugService;
import com.devpilot.center.audit.AuditService;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectMatchResultView;
import com.devpilot.common.api.ProjectMatchResultView.MatchDecision;
import com.devpilot.common.error.ValidationException;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.service.TaskService;
import com.devpilot.task.service.TaskStatusView;
import org.springframework.stereotype.Service;

/**
 * End-to-end entry point for stage 1: analyze -> match -> task -> plan.
 * Auto-matching proceeds; uncertain matches stop for human confirmation.
 */
@Service
public class IntakeService {

    private final BugService bugService;
    private final TaskService taskService;
    private final AuditService auditService;

    public IntakeService(BugService bugService, TaskService taskService, AuditService auditService) {
        this.bugService = bugService;
        this.taskService = taskService;
        this.auditService = auditService;
    }

    public IntakeOutcome intake(String bugId, String actor) {
        BugAnalysisSummary analysis = bugService.analyze(bugId, actor);
        ProjectMatchResultView match = bugService.matchProject(bugId, actor);

        if (match.decision() == MatchDecision.UNMATCHED || match.projectId() == null) {
            auditService.record(actor, "BUG_UNMATCHED", "BUG", bugId,
                    String.join("; ", match.signals()));
            return new IntakeOutcome(analysis, match, null, false);
        }

        TaskEntity task = taskService.createFromBug(analysis, match);
        TaskStatusView status = taskService.status(task.getId());
        auditService.record(actor, "TASK_CREATED", "TASK", task.getId(),
                "bug=" + bugId + " project=" + match.projectId());
        return new IntakeOutcome(analysis, match, status, true);
    }

    public TaskStatusView confirmProject(String taskId, String projectId, String actor) {
        if (projectId == null || projectId.isBlank()) {
            throw new ValidationException("projectId 不能为空");
        }
        return taskService.confirmProject(taskId, projectId, actor);
    }

    public record IntakeOutcome(
            BugAnalysisSummary analysis,
            ProjectMatchResultView match,
            TaskStatusView task,
            boolean taskCreated) {
    }
}
