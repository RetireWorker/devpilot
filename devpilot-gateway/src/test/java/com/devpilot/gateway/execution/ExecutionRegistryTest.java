package com.devpilot.gateway.execution;

import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void runningExecutionsAreMarkedFailedAfterRestart() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        properties.setStateDir(tempDir.toString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        ExecutionRegistry first = new ExecutionRegistry(properties, mapper);
        ExecutionRecord running = record("EX-1", ExecutionStatus.RUNNING);
        first.register(running);
        first.flush();

        assertThat(Files.exists(tempDir.resolve("executions.json"))).isTrue();

        ExecutionRegistry second = new ExecutionRegistry(properties, mapper);
        second.load();

        ExecutionRecord restored = second.require("EX-1");
        assertThat(restored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(restored.getMessage()).contains("restarted");
    }

    @Test
    void idempotencyKeyMapsToFirstExecutionOnly() {
        GatewayProperties properties = new GatewayProperties();
        properties.setStateDir(tempDir.toString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        ExecutionRegistry registry = new ExecutionRegistry(properties, mapper);

        registry.register(record("EX-1", ExecutionStatus.RUNNING));
        registry.register(record("EX-2", ExecutionStatus.RUNNING));

        assertThat(registry.idempotentExecutionId("key-1")).contains("EX-1");
        assertThat(registry.all()).hasSize(2);
    }

    private ExecutionRecord record(String id, ExecutionStatus status) {
        var record = new ExecutionRecord();
        record.setExecutionId(id);
        record.setExecutionKey("key-1");
        record.setStatus(status);
        record.setCreatedAt(Instant.now());
        return record;
    }
}
