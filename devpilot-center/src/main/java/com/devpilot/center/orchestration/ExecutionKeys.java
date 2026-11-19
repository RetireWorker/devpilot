package com.devpilot.center.orchestration;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic execution keys make every retry hit the gateway's idempotency
 * guard instead of launching a second agent run for the same attempt.
 */
@Component
public class ExecutionKeys {

    public String agent(String taskId, String subtaskId, int round) {
        return "agent:" + digest(taskId + "|" + subtaskId + "|" + round);
    }

    public String command(String taskId, String commandKey, int round) {
        return "command:" + digest(taskId + "|" + commandKey + "|" + round);
    }

    private String digest(String value) {
        try {
            var hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
