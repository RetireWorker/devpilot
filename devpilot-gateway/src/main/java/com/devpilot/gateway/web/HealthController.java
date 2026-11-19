package com.devpilot.gateway.web;

import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.gateway.execution.ExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ExecutionService executionService;

    public HealthController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/health")
    public ExecutorHealth health() {
        return executionService.health();
    }
}
