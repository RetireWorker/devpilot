package com.devpilot.gateway.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Injectable process boundary so tests never need to spawn real binaries.
 */
public interface ProcessLauncher {

    ProcessHandle launch(List<String> argv, Path workingDirectory,
                         Path stdoutFile, Path stderrFile) throws IOException;

    interface ProcessHandle {
        boolean waitFor(long millis) throws InterruptedException;

        int exitCode();

        void destroy();

        void destroyForcibly();

        boolean isAlive();
    }
}
