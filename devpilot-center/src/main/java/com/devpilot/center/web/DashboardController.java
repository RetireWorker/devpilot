package com.devpilot.center.web;

import com.devpilot.bug.BugService;
import com.devpilot.bug.api.BugView;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.ProjectCatalog;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.service.TaskService;
import com.devpilot.task.service.TaskStatusView;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregation for the management UI. It composes module services
 * without exposing their entities directly to the browser.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ProjectCatalog projectCatalog;
    private final BugService bugService;
    private final TaskService taskService;
    private final AgentExecutor agentExecutor;

    public DashboardController(ProjectCatalog projectCatalog, BugService bugService,
                               TaskService taskService, AgentExecutor agentExecutor) {
        this.projectCatalog = projectCatalog;
        this.bugService = bugService;
        this.taskService = taskService;
        this.agentExecutor = agentExecutor;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        List<ProjectDescriptor> projects = projectCatalog.listActive();
        List<BugView> bugs = bugService.list(PageRequest.of(0, 200));
        List<TaskSummary> tasks = taskService.list(PageRequest.of(0, 200)).stream()
                .map(this::toSummary).toList();
        Map<String, Long> byState = new LinkedHashMap<>();
        tasks.forEach(task -> byState.merge(task.state(), 1L, Long::sum));
        ExecutorHealth health = safeHealth();

        var payload = new LinkedHashMap<String, Object>();
        payload.put("projects", projects.size());
        payload.put("bugs", bugs.size());
        payload.put("tasks", tasks.size());
        payload.put("tasksByState", byState);
        payload.put("executor", health);
        payload.put("recentBugs", bugs.stream().limit(5).toList());
        payload.put("recentTasks", tasks.stream().limit(5).toList());
        return payload;
    }

    @GetMapping("/tasks/{taskId}/execution")
    public AgentExecutionResult execution(@PathVariable String taskId,
                                          @RequestParam String executionId) {
        return agentExecutor.getResult(executionId);
    }

    private TaskSummary toSummary(TaskEntity task) {
        return new TaskSummary(task.getId(), task.getBugId(), task.getProjectId(),
                task.getTitle(), task.getState().name(), task.getRepairRound(),
                task.getMaxRepairRounds(), task.getMatchedConfidence(),
                task.getUpdatedAt() == null ? null : task.getUpdatedAt().toString());
    }

    private ExecutorHealth safeHealth() {
        try {
            return agentExecutor.health();
        } catch (RuntimeException e) {
            return new ExecutorHealth(false, "unavailable", null, e.getMessage(), null);
        }
    }

    public record TaskSummary(String id, String bugId, String projectId, String title,
                              String state, int repairRound, int maxRepairRounds,
                              double matchedConfidence, String updatedAt) {
    }
}
