package com.devpilot.gateway.web;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.gateway.execution.ExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/executions")
    public ExecutionSubmitResult submit(@Valid @RequestBody AgentExecutionRequest request) {
        return executionService.submitAgent(request);
    }

    @GetMapping("/executions/{id}")
    public ExecutionState status(@PathVariable String id) {
        return executionService.status(id);
    }

    @GetMapping("/executions/{id}/logs")
    public ExecutionLogPage logs(@PathVariable String id,
                                 @RequestParam(required = false) String cursor) {
        return executionService.logs(id, cursor);
    }

    @GetMapping("/executions/{id}/result")
    public AgentExecutionResult result(@PathVariable String id) {
        return executionService.result(id);
    }

    @PostMapping("/executions/{id}/cancel")
    public ExecutionState cancel(@PathVariable String id) {
        executionService.cancel(id);
        return executionService.status(id);
    }

    @PostMapping("/commands")
    public ExecutionSubmitResult submitCommand(@Valid @RequestBody NodeCommandRequest request) {
        return executionService.submitCommand(request);
    }

    @GetMapping("/commands/{id}")
    public ExecutionState commandStatus(@PathVariable String id) {
        return executionService.status(id);
    }

    @GetMapping("/commands/{id}/logs")
    public ExecutionLogPage commandLogs(@PathVariable String id,
                                        @RequestParam(required = false) String cursor) {
        return executionService.logs(id, cursor);
    }

    @GetMapping("/commands/{id}/result")
    public AgentExecutionResult commandResult(@PathVariable String id) {
        return executionService.result(id);
    }

    @PostMapping("/commands/{id}/cancel")
    public ExecutionState commandCancel(@PathVariable String id) {
        executionService.cancel(id);
        return executionService.status(id);
    }

    @GetMapping("/executor/health")
    public ExecutorHealth health() {
        return executionService.health();
    }
}
