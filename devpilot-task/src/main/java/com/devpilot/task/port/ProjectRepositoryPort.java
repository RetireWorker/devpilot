package com.devpilot.task.port;

import com.devpilot.common.api.ProjectDescriptor;

/**
 * Ensures the project mirror exists before a worktree is created. Kept as a
 * port so the task module does not depend on git specifics.
 */
public interface ProjectRepositoryPort {

    /**
     * Clones/fetches the project mirror when missing or stale.
     *
     * @return a human-readable location of the mirror for logs/audit
     */
    String ensureMirror(ProjectDescriptor project);
}
