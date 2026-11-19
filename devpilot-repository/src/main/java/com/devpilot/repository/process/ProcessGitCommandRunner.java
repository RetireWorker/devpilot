package com.devpilot.repository.process;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessGitCommandRunner implements GitCommandRunner {

    @Override
    public CommandResult run(Path workingDirectory, List<String> argv, Duration timeout) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("命令不能为空");
        }
        Path errorFile = null;
        try {
            if (workingDirectory != null && !Files.isDirectory(workingDirectory)) {
                throw new IllegalArgumentException("工作目录不存在: " + workingDirectory);
            }
            var builder = new ProcessBuilder(argv);
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            errorFile = Files.createTempFile("devpilot-cmd-", ".err");
            builder.redirectError(errorFile.toFile());

            var process = builder.start();
            String stdout;
            try (var input = process.getInputStream()) {
                stdout = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new CommandResult(-1, stdout, "命令执行超时", true);
            }
            var stderr = Files.readString(errorFile, StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), stdout, stderr, false);
        } catch (IOException e) {
            throw new IllegalStateException("无法执行命令: " + argv.get(0), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("命令执行被中断", e);
        } finally {
            if (errorFile != null) {
                try {
                    Files.deleteIfExists(errorFile);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }
}
