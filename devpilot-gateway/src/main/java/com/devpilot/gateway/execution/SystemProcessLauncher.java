package com.devpilot.gateway.execution;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SystemProcessLauncher implements ProcessLauncher {

    private static final List<String> SECRET_MARKERS = List.of(
            "TOKEN", "SECRET", "PASSWORD", "CREDENTIAL", "PRIVATE_KEY");

    @Override
    public ProcessHandle launch(List<String> argv, Path workingDirectory,
                                Path stdoutFile, Path stderrFile) throws IOException {
        var builder = new ProcessBuilder(argv)
                .directory(workingDirectory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(stderrFile.toFile()));

        var environment = builder.environment();
        environment.entrySet().removeIf(entry -> isSecretName(entry.getKey()));
        environment.put("CI", "true");

        Process process = builder.start();
        return new Handle(process);
    }

    private boolean isSecretName(String name) {
        String upper = name.toUpperCase();
        return SECRET_MARKERS.stream().anyMatch(upper::contains);
    }

    private static final class Handle implements ProcessHandle {
        private final Process process;
        private volatile boolean killed;

        private Handle(Process process) {
            this.process = process;
        }

        @Override
        public boolean waitFor(long millis) throws InterruptedException {
            return process.waitFor(millis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int exitCode() {
            return process.isAlive() ? -1 : process.exitValue();
        }

        @Override
        public void destroy() {
            process.descendants().forEach(java.lang.ProcessHandle::destroy);
            process.destroy();
        }

        @Override
        public void destroyForcibly() {
            process.descendants().forEach(java.lang.ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            killed = true;
        }

        @Override
        public boolean isAlive() {
            return !killed && process.isAlive();
        }
    }
}
