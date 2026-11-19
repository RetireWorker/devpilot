package com.devpilot.repository.path;

import com.devpilot.common.error.ValidationException;

import java.nio.file.Path;

/**
 * 所有落盘路径必须限制在配置根目录内，拒绝绝对路径与 .. 逃逸。
 */
public final class PathGuard {

    private PathGuard() {
    }

    public static Path resolveUnder(Path root, String relative) {
        if (root == null) {
            throw new ValidationException("工作根目录未配置");
        }
        if (relative == null || relative.isBlank()) {
            throw new ValidationException("相对路径不能为空");
        }
        var candidate = Path.of(relative.trim());
        if (candidate.isAbsolute()) {
            throw new ValidationException("不允许使用绝对路径: " + relative);
        }
        var normalizedRoot = root.toAbsolutePath().normalize();
        var resolved = normalizedRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new ValidationException("路径逃逸工作根目录: " + relative);
        }
        if (resolved.equals(normalizedRoot)) {
            throw new ValidationException("路径不能等于工作根目录");
        }
        return resolved;
    }
}
