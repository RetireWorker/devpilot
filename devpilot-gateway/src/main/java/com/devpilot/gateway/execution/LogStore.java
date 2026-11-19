package com.devpilot.gateway.execution;

import com.devpilot.common.api.ExecutionLogPage;
import com.devpilot.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Logs live on disk. The cursor is the next line offset, which makes paging
 * restart-safe and avoids loading an entire build log into memory.
 */
@Component
public class LogStore {

    private final Path logDir;
    private final long maxBytes;

    public LogStore(GatewayProperties properties) {
        this.logDir = Paths.get(properties.getStateDir()).toAbsolutePath().resolve("logs");
        this.maxBytes = properties.getMaxLogBytes();
    }

    public Path newLogFile(String executionId) {
        try {
            Files.createDirectories(logDir);
            return logDir.resolve(executionId + ".log");
        } catch (IOException e) {
            throw new IllegalStateException("cannot create log directory", e);
        }
    }

    public ExecutionLogPage page(String executionId, String cursor) {
        Path file = logDir.resolve(executionId + ".log");
        if (!Files.exists(file)) {
            return new ExecutionLogPage(executionId, List.of(), null, false);
        }
        int offset = parseCursor(cursor);
        var lines = new ArrayList<ExecutionLogPage.LogLine>();
        int next = offset;
        boolean truncated = false;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (index < offset) {
                    index++;
                    continue;
                }
                if (lines.size() >= 500) {
                    truncated = true;
                    break;
                }
                lines.add(new ExecutionLogPage.LogLine(Instant.now(), "combined", line));
                index++;
                next = index;
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read execution log", e);
        }
        return new ExecutionLogPage(executionId, lines, truncated ? String.valueOf(next) : null, truncated);
    }

    public void trim(Path logFile) {
        try {
            if (Files.exists(logFile) && Files.size(logFile) > maxBytes) {
                Files.writeString(logFile,
                        "\n[devpilot] log truncated after exceeding " + maxBytes + " bytes\n",
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException ignored) {
            // Trimming is best-effort; execution correctness must not depend on it.
        }
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
