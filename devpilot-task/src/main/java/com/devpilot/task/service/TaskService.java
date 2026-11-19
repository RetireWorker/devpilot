package com.devpilot.task.service;

import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.ProjectMatchResultView;
import com.devpilot.common.api.SubtaskSpec;
import com.devpilot.common.api.TaskPlanView;
import com.devpilot.common.api.VerificationResultView;
import com.devpilot.common.api.WorkspaceProvisionRequest;
import com.devpilot.common.domain.TaskEvent;
import com.devpilot.common.domain.TaskState;
import com.devpilot.common.domain.TaskTransition;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.task.config.TaskProperties;
import com.devpilot.task.domain.ApprovalEntity;
import com.devpilot.task.domain.CodeChangeEntity;
import com.devpilot.task.domain.ExecutionEventEntity;
import com.devpilot.task.domain.ReviewEntity;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.domain.TaskPlanEntity;
import com.devpilot.task.domain.TaskSubtaskEntity;
import com.devpilot.task.domain.TestResultEntity;
import com.devpilot.task.domain.WorkspaceEntity;
import com.devpilot.task.policy.RepairLoopPolicy;
import com.devpilot.task.port.ProjectLookupPort;
import com.devpilot.task.port.ProjectRepositoryPort;
import com.devpilot.task.port.TaskPlanPort;
import com.devpilot.task.port.TaskWorkspacePort;
import com.devpilot.task.repository.ApprovalRepository;
import com.devpilot.task.repository.CodeChangeRepository;
import com.devpilot.task.repository.ExecutionEventRepository;
import com.devpilot.task.repository.ReviewRepository;
import com.devpilot.task.repository.TaskPlanRepository;
import com.devpilot.task.repository.TaskRepository;
import com.devpilot.task.repository.TaskSubtaskRepository;
import com.devpilot.task.repository.TestResultRepository;
import com.devpilot.task.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskRepository taskRepository;
    private final TaskPlanRepository planRepository;
    private final TaskSubtaskRepository subtaskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CodeChangeRepository codeChangeRepository;
    private final TestResultRepository testResultRepository;
    private final ReviewRepository reviewRepository;
    private final ApprovalRepository approvalRepository;
    private final ExecutionEventRepository eventRepository;
    private final TaskStateMachine stateMachine;
    private final TaskProperties properties;
    private final TaskPlanPort planPort;
    private final TaskWorkspacePort workspacePort;
    private final ProjectLookupPort projectLookupPort;
    private final ProjectRepositoryPort projectRepositoryPort;

    public TaskService(TaskRepository taskRepository,
                       TaskPlanRepository planRepository,
                       TaskSubtaskRepository subtaskRepository,
                       WorkspaceRepository workspaceRepository,
                       CodeChangeRepository codeChangeRepository,
                       TestResultRepository testResultRepository,
                       ReviewRepository reviewRepository,
                       ApprovalRepository approvalRepository,
                       ExecutionEventRepository eventRepository,
                       TaskStateMachine stateMachine,
                       TaskProperties properties,
                       TaskPlanPort planPort,
                       TaskWorkspacePort workspacePort,
                       ProjectLookupPort projectLookupPort,
                       ProjectRepositoryPort projectRepositoryPort) {
        this.taskRepository = taskRepository;
        this.planRepository = planRepository;
        this.subtaskRepository = subtaskRepository;
        this.workspaceRepository = workspaceRepository;
        this.codeChangeRepository = codeChangeRepository;
        this.testResultRepository = testResultRepository;
        this.reviewRepository = reviewRepository;
        this.approvalRepository = approvalRepository;
        this.eventRepository = eventRepository;
        this.stateMachine = stateMachine;
        this.properties = properties;
        this.planPort = planPort;
        this.workspacePort = workspacePort;
        this.projectLookupPort = projectLookupPort;
        this.projectRepositoryPort = projectRepositoryPort;
    }

    @Transactional
    public TaskEntity createFromBug(BugAnalysisSummary bug, ProjectMatchResultView match) {
        if (bug == null || bug.bugId() == null) {
            throw new ValidationException("BUG 分析结果缺少 bugId");
        }
        if (match == null || match.projectId() == null) {
            throw new ValidationException("项目匹配结果缺少 projectId");
        }
        if (taskRepository.findByBugId(bug.bugId()).isPresent()) {
            throw new ConflictException("该 BUG 已存在研发任务: " + bug.bugId());
        }

        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TaskEntity task = TaskEntity.create(taskId, bug.bugId(), match.projectId(), bug.title(),
                bug.description(), match.confidence(), serialize(bug));
        task.setMaxRepairRounds(properties.getMaxRepairRounds());
        taskRepository.save(task);

        stateMachine.transition(taskId, TaskEvent.BUG_SUBMITTED, "system", "BUG 受理", null);
        stateMachine.transition(taskId, TaskEvent.BUG_ANALYZED, "system", bug.problemType(), null);

        if (match.decision() == ProjectMatchResultView.MatchDecision.AUTO_MATCHED) {
            stateMachine.transition(taskId, TaskEvent.PROJECT_MATCHED, "system",
                    "confidence=" + match.confidence(), null);
            generatePlan(taskId, null, "system");
        } else {
            stateMachine.transition(taskId, TaskEvent.NEED_HUMAN, "system",
                    "项目匹配置信度不足: " + match.confidence(), null);
        }
        return taskRepository.findById(taskId).orElseThrow();
    }

    @Transactional
    public TaskStatusView confirmProject(String taskId, String projectId, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.WAITING_PROJECT_CONFIRM) {
            throw new ConflictException("当前状态不允许确认项目: " + task.getState());
        }
        projectLookupPort.findById(projectId)
                .orElseThrow(() -> new ValidationException("项目未注册: " + projectId));
        task.setProjectId(projectId);
        task.touch();
        taskRepository.save(task);
        stateMachine.transition(taskId, TaskEvent.PROJECT_CONFIRMED, actor, "人工确认项目", null);
        generatePlan(taskId, null, actor);
        return status(taskId);
    }

    @Transactional
    public TaskStatusView generatePlan(String taskId, PlanRequest request, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.PLANNING) {
            throw new ConflictException("当前状态不允许生成计划: " + task.getState());
        }
        TaskPlanView plan = request != null && request.subtasks() != null && !request.subtasks().isEmpty()
                ? new TaskPlanView(taskId, task.getProjectId(),
                request.objective() == null ? task.getObjective() : request.objective(),
                request.subtasks(),
                request.requiresHumanConfirmation() != null && request.requiresHumanConfirmation())
                : planPort.generate(readBug(task), requireProject(task), task.getMatchedConfidence());

        planRepository.deleteByTaskId(taskId);
        subtaskRepository.deleteByTaskId(taskId);
        planRepository.save(TaskPlanEntity.create(taskId, plan.objective(), plan.requiresHumanConfirmation()));
        for (SubtaskSpec spec : plan.subtasks()) {
            subtaskRepository.save(TaskSubtaskEntity.create(taskId, spec.id(), spec.role(), spec.type(),
                    spec.dependsOn(), spec.inputs(), spec.outputs(), spec.allowedPaths(), spec.acceptance()));
        }
        stateMachine.transition(taskId, TaskEvent.PLAN_CREATED, actor, "子任务数=" + plan.subtasks().size(), null);
        return status(taskId);
    }

    @Transactional
    public TaskStatusView approvePlan(String taskId, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.WAITING_CONFIRM) {
            throw new ConflictException("当前状态不允许确认计划: " + task.getState());
        }
        stateMachine.transition(taskId, TaskEvent.PLAN_CONFIRMED, actor, "人工确认计划", null);
        return status(taskId);
    }

    @Transactional
    public TaskStatusView prepareWorkspace(String taskId, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.READY) {
            throw new ConflictException("当前状态不允许准备工作区: " + task.getState());
        }
        ProjectDescriptor project = requireProject(task);
        stateMachine.transition(taskId, TaskEvent.WORKSPACE_REQUESTED, actor, "开始准备工作区", null);

        String workspaceId = "WS-" + taskId;
        String branch = "ai/" + taskId.toLowerCase();
        String relativeRoot = project.workspaceRoot() == null ? "default" : project.workspaceRoot();
        // 先确保镜像仓库存在，再创建 worktree；否则 git worktree add 没有可用的仓库根。
        String mirrorLocation = projectRepositoryPort.ensureMirror(project);
        WorkspaceProvisionRequest request = new WorkspaceProvisionRequest(
                taskId, project.id(), workspaceId, branch,
                project.targetRef() == null ? project.defaultBranch() : project.targetRef(),
                relativeRoot, taskId);
        var result = workspacePort.provision(request);

        workspaceRepository.save(WorkspaceEntity.create(result.workspaceId(), taskId, project.id(),
                result.relativePath(), result.branch(), result.baseRef(), result.headCommit(), "READY"));
        task.setWorkspaceId(result.workspaceId());
        task.setBranch(result.branch());
        task.touch();
        taskRepository.save(task);
        stateMachine.transition(taskId, TaskEvent.WORKSPACE_READY, actor,
                result.relativePath() + " (mirror=" + mirrorLocation + ")", null);
        return status(taskId);
    }

    @Transactional
    public TaskStatusView recordCommit(String taskId, CodeChangeView change, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.DEVELOPING) {
            throw new ConflictException("当前状态不允许提交代码: " + task.getState());
        }
        codeChangeRepository.save(CodeChangeEntity.create(change));
        stateMachine.transition(taskId, TaskEvent.CODE_COMMITTED, actor, change.commitSha(), null);
        return status(taskId);
    }

    @Transactional
    public TaskStatusView recordTestResult(String taskId, boolean passed, Integer exitCode,
                                           String reportRef, String summary, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.TESTING) {
            throw new ConflictException("当前状态不允许记录测试结果: " + task.getState());
        }
        testResultRepository.save(TestResultEntity.create(taskId, task.getRepairRound(), passed,
                exitCode, reportRef, summary));
        if (passed) {
            stateMachine.transition(taskId, TaskEvent.TEST_PASSED, actor, "测试通过", null);
        } else {
            handleFailure(task, TaskEvent.TEST_FAILED, actor, "测试失败", summary);
        }
        return status(taskId);
    }

    @Transactional
    public TaskStatusView recordReview(String taskId, VerificationResultView review, String actor) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.REVIEWING) {
            throw new ConflictException("当前状态不允许记录审查结果: " + task.getState());
        }
        reviewRepository.save(ReviewEntity.create(taskId, task.getRepairRound(), review.passed(),
                review.blocking(), review.warnings(), review.risks(), review.summary()));
        if (review.passed()) {
            stateMachine.transition(taskId, TaskEvent.REVIEW_PASSED, actor, review.summary(), null);
        } else {
            handleFailure(task, TaskEvent.REVIEW_FAILED, actor, "审查未通过",
                    String.join("; ", review.blocking()));
        }
        return status(taskId);
    }

    private void handleFailure(TaskEntity task, TaskEvent failEvent, String actor,
                               String summary, String detail) {
        boolean exhausted = RepairLoopPolicy.registerFailedAttempt(task);
        task.touch();
        taskRepository.save(task);
        if (exhausted) {
            stateMachine.transition(task.getId(), TaskEvent.NEED_HUMAN, actor,
                    "自动修复轮次耗尽: " + detail, null);
        } else {
            stateMachine.transition(task.getId(), failEvent, actor, summary, null);
        }
    }

    @Transactional
    public DeliveryOutcome approveDelivery(String taskId, String approvalType,
                                           String approver, String comment) {
        TaskEntity task = requireTask(taskId);
        if (task.getState() != TaskState.WAITING_APPROVAL) {
            throw new ConflictException("当前状态不允许交付审批: " + task.getState());
        }
        String type = approvalType == null || approvalType.isBlank() ? "DELIVERY" : approvalType;
        approvalRepository.save(ApprovalEntity.create(taskId, type, "APPROVED", approver, comment));
        if (!properties.isDeliveryEnabled()) {
            return new DeliveryOutcome(taskId, false, task.getState(),
                    "第一阶段未启用代码推送/dcommit，审批已记录，不会执行任何交付动作");
        }
        return new DeliveryOutcome(taskId, true, task.getState(), "交付能力已启用，等待执行器处理");
    }

    @Transactional
    public TaskStatusView cancel(String taskId, String actor, String reason) {
        requireTask(taskId);
        stateMachine.transition(taskId, TaskEvent.CANCEL, actor, reason, null);
        return status(taskId);
    }

    @Transactional(readOnly = true)
    public TaskStatusView status(String taskId) {
        TaskEntity task = requireTask(taskId);
        return new TaskStatusView(task.getId(), task.getBugId(), task.getProjectId(), task.getState(),
                task.getRepairRound(), task.getMaxRepairRounds(), task.getMatchedConfidence(),
                task.getWorkspaceId(), workspaceRelativePath(taskId), task.getBranch(),
                TaskTransition.allowedEvents(task.getState()), task.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public TaskReportView report(String taskId) {
        TaskEntity task = requireTask(taskId);
        List<TaskReportView.SubtaskView> subtasks = subtaskRepository.findByTaskIdOrderByIdAsc(taskId)
                .stream()
                .map(s -> new TaskReportView.SubtaskView(s.getSubtaskKey(), s.getRole().name(),
                        s.getType(), s.getStatus(), s.getDependsOn(), s.getAllowedPaths(), s.getAcceptance()))
                .toList();
        CodeChangeView change = codeChangeRepository.findFirstByTaskIdOrderByIdDesc(taskId)
                .map(CodeChangeEntity::toView).orElse(null);
        List<TaskReportView.TestView> tests = testResultRepository.findByTaskIdOrderByIdAsc(taskId)
                .stream()
                .map(t -> new TaskReportView.TestView(t.getRound(), t.isPassed(), t.getExitCode(),
                        t.getSummary(), t.getCreatedAt()))
                .toList();
        List<TaskReportView.ReviewView> reviews = reviewRepository.findByTaskIdOrderByIdAsc(taskId)
                .stream()
                .map(r -> new TaskReportView.ReviewView(r.getRound(), r.isPassed(), r.getBlocking(),
                        r.getWarnings(), r.getRisks(), r.getSummary(), r.getCreatedAt()))
                .toList();
        List<TaskReportView.ApprovalView> approvals = approvalRepository.findByTaskIdOrderByIdAsc(taskId)
                .stream()
                .map(a -> new TaskReportView.ApprovalView(a.getType(), a.getDecision(), a.getApprover(),
                        a.getComment(), a.getCreatedAt()))
                .toList();
        List<TaskReportView.EventView> events = eventRepository.findByTaskIdOrderByIdAsc(taskId)
                .stream()
                .map(e -> new TaskReportView.EventView(e.getEvent().name(), e.getFromState().name(),
                        e.getToState().name(), e.getActor(), e.getDetail(), e.getCreatedAt()))
                .toList();
        return new TaskReportView(task.getId(), task.getBugId(), task.getProjectId(), task.getTitle(),
                task.getObjective(), task.getState(), task.getRepairRound(), task.getMaxRepairRounds(),
                task.getMatchedConfidence(), task.getWorkspaceId(), task.getBranch(), subtasks, change,
                tests, reviews, approvals, events, nextActions(task.getState()));
    }

    @Transactional(readOnly = true)
    public Page<TaskEntity> list(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    private static List<String> nextActions(TaskState state) {
        List<String> actions = new ArrayList<>();
        switch (state) {
            case WAITING_PROJECT_CONFIRM -> actions.add("确认项目后 POST /api/tasks/{id}/plan");
            case WAITING_CONFIRM -> actions.add("确认计划 POST /api/tasks/{id}/approve-plan");
            case READY -> actions.add("准备工作区 POST /api/tasks/{id}/prepare-workspace");
            case DEVELOPING -> actions.add("等待开发 Agent 提交代码差异");
            case TESTING -> actions.add("执行测试并回传退出码与报告");
            case REVIEWING -> actions.add("执行独立代码审查");
            case WAITING_APPROVAL -> actions.add("人工审批后进入交付（阶段一不自动推送）");
            case FAILED_NEEDS_HUMAN -> actions.add("人工介入修复或调整计划");
            default -> {
            }
        }
        return actions;
    }

    private String workspaceRelativePath(String taskId) {
        return workspaceRepository.findByTaskId(taskId)
                .map(WorkspaceEntity::getRelativePath)
                .orElse(null);
    }

    private TaskEntity requireTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在: " + taskId));
    }

    private ProjectDescriptor requireProject(TaskEntity task) {
        return projectLookupPort.findById(task.getProjectId())
                .orElseThrow(() -> new ValidationException("项目未注册: " + task.getProjectId()));
    }

    private BugAnalysisSummary readBug(TaskEntity task) {
        try {
            return MAPPER.readValue(task.getBugAnalysisJson(), BugAnalysisSummary.class);
        } catch (Exception e) {
            throw new ValidationException("任务缺少可用的 BUG 分析数据: " + task.getId());
        }
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化 BUG 分析结果", e);
        }
    }
}
