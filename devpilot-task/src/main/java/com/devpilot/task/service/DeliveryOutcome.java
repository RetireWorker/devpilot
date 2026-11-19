package com.devpilot.task.service;

import com.devpilot.common.domain.TaskState;

public record DeliveryOutcome(
        String taskId,
        boolean deliveryEnabled,
        TaskState state,
        String message) {
}
