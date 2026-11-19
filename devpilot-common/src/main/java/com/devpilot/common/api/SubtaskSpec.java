package com.devpilot.common.api;

import java.util.List;

public record SubtaskSpec(
        String id,
        AgentRole role,
        String type,
        List<String> dependsOn,
        List<String> inputs,
        List<String> outputs,
        List<String> allowedPaths,
        String acceptance) {
}
