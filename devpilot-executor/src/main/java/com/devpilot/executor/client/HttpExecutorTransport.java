package com.devpilot.executor.client;

import com.devpilot.common.api.AgentExecutionRequest;
import com.devpilot.common.api.AgentExecutionResult;
import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.common.api.ExecutionState;
import com.devpilot.common.api.ExecutionSubmitResult;
import com.devpilot.common.api.ExecutorHealth;
import com.devpilot.common.api.NodeCommandRequest;
import com.devpilot.common.error.DevpilotException;
import com.devpilot.executor.config.ExecutorProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpExecutorTransport implements ExecutorTransport {

    private final RestClient client;

    public HttpExecutorTransport(ExecutorProperties.Endpoint endpoint) {
        if (endpoint.getEndpoint() == null || endpoint.getEndpoint().isBlank()) {
            throw new IllegalArgumentException("executor endpoint is required");
        }
        var factory = new SimpleClientHttpRequestFactory();
        var connect = endpoint.getConnectTimeout();
        var read = endpoint.getReadTimeout();
        if (connect != null) {
            factory.setConnectTimeout((int) connect.toMillis());
        }
        if (read != null) {
            factory.setReadTimeout((int) read.toMillis());
        }
        var builder = RestClient.builder()
                .baseUrl(endpoint.getEndpoint())
                .requestFactory(factory);
        if (endpoint.getToken() != null && !endpoint.getToken().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + endpoint.getToken());
        }
        this.client = builder.build();
    }

    @Override
    public ExecutionSubmitResult submitExecution(AgentExecutionRequest request) {
        return post("/api/executions", request, ExecutionSubmitResult.class);
    }

    @Override
    public ExecutionState status(String executionId) {
        return get("/api/executions/" + executionId, ExecutionState.class);
    }

    @Override
    public ExecutionLogPage logs(String executionId, String cursor) {
        String path = "/api/executions/" + executionId + "/logs"
                + (cursor == null ? "" : "?cursor=" + cursor);
        return get(path, ExecutionLogPage.class);
    }

    @Override
    public AgentExecutionResult result(String executionId) {
        return get("/api/executions/" + executionId + "/result", AgentExecutionResult.class);
    }

    @Override
    public void cancel(String executionId) {
        post("/api/executions/" + executionId + "/cancel", null, ExecutionState.class);
    }

    @Override
    public ExecutionSubmitResult submitCommand(NodeCommandRequest request) {
        return post("/api/commands", request, ExecutionSubmitResult.class);
    }

    @Override
    public ExecutionState commandStatus(String executionId) {
        return get("/api/commands/" + executionId, ExecutionState.class);
    }

    @Override
    public ExecutionLogPage commandLogs(String executionId, String cursor) {
        String path = "/api/commands/" + executionId + "/logs"
                + (cursor == null ? "" : "?cursor=" + cursor);
        return get(path, ExecutionLogPage.class);
    }

    @Override
    public AgentExecutionResult commandResult(String executionId) {
        return get("/api/commands/" + executionId + "/result", AgentExecutionResult.class);
    }

    @Override
    public void commandCancel(String executionId) {
        post("/api/commands/" + executionId + "/cancel", null, ExecutionState.class);
    }

    @Override
    public ExecutorHealth health() {
        return get("/health", ExecutorHealth.class);
    }

    private <T> T get(String path, Class<T> type) {
        try {
            return client.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            throw new DevpilotException("executor call failed: GET " + path, e);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            var spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
            var response = body == null ? spec.retrieve() : spec.body(body).retrieve();
            return response.body(type);
        } catch (RestClientException e) {
            throw new DevpilotException("executor call failed: POST " + path, e);
        }
    }
}
