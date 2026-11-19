package com.devpilot.common.api;

import java.util.List;

public record TestPlanView(
        String taskId,
        List<TestScenario> scenarios,
        List<String> existingTests,
        List<String> gaps) {
}
