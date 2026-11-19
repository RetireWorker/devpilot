package com.devpilot.center.web;

import com.devpilot.center.orchestration.TaskPipelineService;
import com.devpilot.common.domain.TaskState;
import com.devpilot.task.service.TaskService;
import com.devpilot.task.service.TaskStatusView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行入口：按当前状态驱动开发或测试/审查阶段，避免只返回 202 却什么都不做。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskExecutionController {

    private final TaskService taskService;
    private final TaskPipelineService pipelineService;

    public TaskExecutionController(TaskService taskService, TaskPipelineService pipelineService) {
        this.taskService = taskService;
        this.pipelineService = pipelineService;
    }

    @PostMapping("/{taskId}/execute")
    public Object execute(@PathVariable String taskId,
                          @RequestParam(defaultValue = "system") String actor) {
        TaskStatusView task = taskService.status(taskId);
        if (task.state() == TaskState.DEVELOPING) {
            return pipelineService.develop(taskId, actor);
        }
        if (task.state() == TaskState.TESTING) {
            return pipelineService.verify(taskId, actor);
        }
        // 其他状态没有可自动执行的动作，直接返回当前状态供调用方决策。
        return task;
    }
}
