package com.devpilot.repository.process;

public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    public boolean success() {
        return exitCode == 0 && !timedOut;
    }

    public String combinedOutput() {
        if (stderr == null || stderr.isBlank()) {
            return stdout == null ? "" : stdout;
        }
        return (stdout == null ? "" : stdout + "\n") + stderr;
    }
}
