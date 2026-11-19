package com.devpilot.task.port;

import com.devpilot.common.api.WorkspaceProvisionRequest;
import com.devpilot.common.api.WorkspaceProvisionResult;

/**
 * Boundary to the repository module. The center wires the real adapter; the
 * task module never touches git directly.
 */
public interface TaskWorkspacePort {

    WorkspaceProvisionResult provision(WorkspaceProvisionRequest request);
}
