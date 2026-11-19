package com.devpilot.common.api;

import java.util.List;

public record VerificationResultView(
        String kind,
        boolean passed,
        List<String> blocking,
        List<String> warnings,
        List<String> risks,
        String summary) {
}
