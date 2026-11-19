package com.devpilot.center.web;

import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executor")
public class ExecutorController {

    private final AgentExecutor executor;

    public ExecutorController(AgentExecutor executor) {
        this.executor = executor;
    }

    @GetMapping("/health")
    public ExecutorHealth health() {
        try {
            return executor.health();
        } catch (RuntimeException e) {
            return new ExecutorHealth(false, "unavailable", null, e.getMessage(), null);
        }
    }

    @GetMapping("/executions/{id}")
    public ExecutionState status(@PathVariable String id) {
        return executor.getStatus(id);
    }

    @GetMapping("/executions/{id}/logs")
    public ExecutionLogPage logs(@PathVariable String id,
                                 @RequestParam(required = false) String cursor) {
        return executor.getLogs(id, cursor);
    }

    @PostMapping("/executions/{id}/cancel")
    public ExecutionState cancel(@PathVariable String id) {
        executor.cancel(id);
        return executor.getStatus(id);
    }
}
