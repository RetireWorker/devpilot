package com.devpilot.common.api;

import java.util.List;

public record RootCauseView(
        String rootCause,
        double confidence,
        List<String> evidence,
        List<String> verificationSteps,
        List<String> alternativeHypotheses) {
}
