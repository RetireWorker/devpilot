package com.devpilot.gateway.execution;

import com.devpilot.common.api.ExecutionStatus;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRegistry.class);

    private final Map<String, ExecutionRecord> records = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path stateFile;

    public ExecutionRegistry(GatewayProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.stateFile = Paths.get(properties.getStateDir()).toAbsolutePath().resolve("executions.json");
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(stateFile)) {
            return;
        }
        try {
            var restored = objectMapper.readValue(stateFile.toFile(), PersistedState.class);
            if (restored != null && restored.records != null) {
                for (ExecutionRecord record : restored.records) {
                    if (record.getStatus() == ExecutionStatus.RUNNING
                            || record.getStatus() == ExecutionStatus.PENDING) {
                        record.setStatus(ExecutionStatus.FAILED);
                        record.setFinishedAt(Instant.now());
                        record.setMessage("gateway restarted while execution was in flight");
                    }
                    records.put(record.getExecutionId(), record);
                    if (record.getExecutionKey() != null) {
                        idempotency.put(record.getExecutionKey(), record.getExecutionId());
                    }
                }
            }
            log.info("restored {} execution records", records.size());
        } catch (IOException e) {
            log.warn("cannot restore execution state: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void flush() {
        persist();
    }

    public Optional<ExecutionRecord> find(String executionId) {
        return Optional.ofNullable(records.get(executionId));
    }

    public ExecutionRecord require(String executionId) {
        return find(executionId).orElseThrow(() -> new NotFoundException("unknown execution: " + executionId));
    }

    public Optional<String> idempotentExecutionId(String executionKey) {
        if (executionKey == null || executionKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idempotency.get(executionKey));
    }

    public void register(ExecutionRecord record) {
        records.put(record.getExecutionId(), record);
        if (record.getExecutionKey() != null && !record.getExecutionKey().isBlank()) {
            // Keep the first binding for in-flight or successful runs (duplicate
            // submissions must not fork), but let a terminal failure be retried.
            String previous = idempotency.get(record.getExecutionKey());
            ExecutionRecord old = previous == null ? null : records.get(previous);
            boolean retryable = old == null
                    || old.getStatus() == ExecutionStatus.FAILED
                    || old.getStatus() == ExecutionStatus.TIMED_OUT
                    || old.getStatus() == ExecutionStatus.CANCELLED;
            if (retryable) {
                idempotency.put(record.getExecutionKey(), record.getExecutionId());
            }
        }
        persist();
    }

    public void update(ExecutionRecord record) {
        records.put(record.getExecutionId(), record);
        persist();
    }

    public Collection<ExecutionRecord> all() {
        return List.copyOf(records.values());
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(stateFile.getParent());
            var state = new PersistedState();
            state.records = new ArrayList<>(records.values());
            Path temp = stateFile.resolveSibling("executions.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
            try {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("cannot persist execution state: {}", e.getMessage());
        }
    }

    public static class PersistedState {
        public List<ExecutionRecord> records;

        public List<ExecutionRecord> getRecords() {
            return records;
        }

        public void setRecords(List<ExecutionRecord> records) {
            this.records = records;
        }
    }
}
