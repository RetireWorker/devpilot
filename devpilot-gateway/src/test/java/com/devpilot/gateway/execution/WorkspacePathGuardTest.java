package com.devpilot.gateway.execution;

import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePathGuardTest {

    @TempDir
    Path root;

    @Test
    void rejectsTraversalAndAbsolutePaths() throws Exception {
        Files.createDirectories(root.resolve("task-1"));
        WorkspacePathGuard guard = guard();

        assertThatThrownBy(() -> guard.resolve("../etc/passwd"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> guard.resolve("/tmp"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolvesExistingWorkspaceInsideRoot() throws Exception {
        Path workspace = Files.createDirectories(root.resolve("task-1"));
        WorkspacePathGuard guard = guard();

        assertThat(guard.resolve("task-1")).isEqualTo(workspace.toRealPath());
    }

    @Test
    void rejectsMissingWorkspace() {
        assertThatThrownBy(() -> guard().resolve("missing"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not exist");
    }

    private WorkspacePathGuard guard() {
        GatewayProperties properties = new GatewayProperties();
        properties.setWorkspaceRoot(root.toString());
        return new WorkspacePathGuard(properties);
    }
}
