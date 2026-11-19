package com.devpilot.common.api;

import java.time.Duration;
import java.util.Map;

/**
 * A request to run a named, pre-approved command on an execution node. It
 * never carries a shell string: the gateway maps commandKey to an argv
 * template and validates every parameter.
 */
public record NodeCommandRequest(
        String executionKey,
        String taskId,
        String commandKey,
        String relativeWorkspace,
        Map<String, String> parameters,
        Duration timeout) {
}
