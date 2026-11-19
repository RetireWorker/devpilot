package com.devpilot.repository.path;

import com.devpilot.common.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathGuardTest {

    @TempDir
    Path root;

    @Test
    void resolvesChildInsideRoot() {
        var resolved = PathGuard.resolveUnder(root, "project-a/ws-1");

        assertThat(resolved.toString()).startsWith(root.toAbsolutePath().normalize().toString());
        assertThat(resolved.getFileName().toString()).isEqualTo("ws-1");
    }

    @Test
    void rejectsParentTraversal() {
        assertThatThrownBy(() -> PathGuard.resolveUnder(root, "../etc/passwd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("逃逸");
    }

    @Test
    void rejectsNestedTraversal() {
        assertThatThrownBy(() -> PathGuard.resolveUnder(root, "a/../../b"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsAbsolutePath() {
        assertThatThrownBy(() -> PathGuard.resolveUnder(root, "/tmp/evil"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> PathGuard.resolveUnder(root, "  "))
                .isInstanceOf(ValidationException.class);
    }
}
