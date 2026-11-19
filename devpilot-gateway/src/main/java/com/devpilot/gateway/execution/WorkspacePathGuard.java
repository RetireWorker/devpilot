package com.devpilot.gateway.execution;

import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Callers only send a workspace-relative path. Absolute paths and traversal
 * are rejected before anything touches the filesystem.
 */
@Component
public class WorkspacePathGuard {

    private final Path root;

    public WorkspacePathGuard(GatewayProperties properties) {
        this.root = Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ValidationException("workspace relative path is required");
        }
        String value = relativePath.replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new ValidationException("absolute workspace paths are not allowed");
        }
        Path resolved = root.resolve(value).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidationException("workspace path escapes the configured root");
        }
        try {
            Files.createDirectories(root);
            if (!Files.exists(resolved)) {
                throw new ValidationException("workspace does not exist: " + relativePath);
            }
            Path realRoot = root.toRealPath();
            Path realResolved = resolved.toRealPath();
            if (!realResolved.startsWith(realRoot)) {
                throw new ValidationException("workspace symlink escapes the configured root");
            }
            return realResolved;
        } catch (IOException e) {
            throw new ValidationException("cannot resolve workspace: " + e.getMessage());
        }
    }

    public Path root() {
        return root;
    }
}
