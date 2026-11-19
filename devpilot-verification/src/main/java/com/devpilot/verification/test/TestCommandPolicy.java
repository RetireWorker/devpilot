package com.devpilot.verification.test;

import com.devpilot.common.error.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * Maps a project's registered build key to an argv array. Callers can never
 * pass a shell string, and unknown keys are rejected rather than guessed.
 */
public final class TestCommandPolicy {

    private static final Map<String, List<String>> COMMANDS = Map.of(
            "maven-test", List.of("mvn", "-B", "-q", "test"),
            "maven-verify", List.of("mvn", "-B", "-q", "clean", "verify"),
            "maven-package", List.of("mvn", "-B", "-q", "clean", "package"),
            "npm-test", List.of("npm", "test", "--", "--runInBand"),
            "gradle-test", List.of("gradle", "--no-daemon", "test")
    );

    private TestCommandPolicy() {
    }

    public static List<String> argv(String buildKey) {
        if (buildKey == null || buildKey.isBlank()) {
            throw new ValidationException("未配置测试命令键");
        }
        List<String> command = COMMANDS.get(buildKey.trim().toLowerCase());
        if (command == null) {
            throw new ValidationException("不允许的测试命令键: " + buildKey);
        }
        return command;
    }

    public static boolean isAllowed(String buildKey) {
        return buildKey != null && COMMANDS.containsKey(buildKey.trim().toLowerCase());
    }

    public static List<String> allowedKeys() {
        return List.copyOf(COMMANDS.keySet());
    }
}
