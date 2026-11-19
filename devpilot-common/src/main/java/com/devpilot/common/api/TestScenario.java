package com.devpilot.common.api;

import java.util.List;

public record TestScenario(
        String name,
        String type,
        List<String> steps,
        String expectedResult,
        boolean reproduction) {
}
