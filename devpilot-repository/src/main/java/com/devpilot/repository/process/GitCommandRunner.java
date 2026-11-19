package com.devpilot.repository.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 所有 Git/SVN 调用都通过 argv 列表执行，禁止拼接 shell 字符串。
 * 抽象成接口便于测试注入假实现，不打真实仓库。
 */
public interface GitCommandRunner {

    CommandResult run(Path workingDirectory, List<String> argv, Duration timeout);
}
