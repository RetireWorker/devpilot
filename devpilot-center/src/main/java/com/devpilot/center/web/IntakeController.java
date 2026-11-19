package com.devpilot.center.web;

import com.devpilot.center.orchestration.IntakeService;
import com.devpilot.task.service.TaskStatusView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/bugs/{bugId}")
    public IntakeService.IntakeOutcome intake(@PathVariable String bugId,
                                              @RequestParam(defaultValue = "api") String actor) {
        return intakeService.intake(bugId, actor);
    }

    @PostMapping("/tasks/{taskId}/confirm-project")
    public TaskStatusView confirmProject(@PathVariable String taskId,
                                         @RequestParam String projectId,
                                         @RequestParam(defaultValue = "human") String actor) {
        return intakeService.confirmProject(taskId, projectId, actor);
    }
}
