package com.devpilot.center.web;

import com.devpilot.center.orchestration.TaskPipelineService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline/tasks")
public class PipelineController {

    private final TaskPipelineService pipelineService;

    public PipelineController(TaskPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/{taskId}/develop")
    public TaskPipelineService.DevelopOutcome develop(@PathVariable String taskId,
                                                      @RequestParam(defaultValue = "system") String actor) {
        return pipelineService.develop(taskId, actor);
    }

    @PostMapping("/{taskId}/verify")
    public TaskPipelineService.VerifyOutcome verify(@PathVariable String taskId,
                                                    @RequestParam(defaultValue = "system") String actor) {
        return pipelineService.verify(taskId, actor);
    }
}
