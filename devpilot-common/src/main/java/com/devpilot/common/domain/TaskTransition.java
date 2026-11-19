package com.devpilot.common.domain;

import java.util.List;
import java.util.Map;

/**
 * Explicit transition table. Keeping it declarative makes "who may move the
 * task where" auditable and prevents ad-hoc state writes.
 */
public final class TaskTransition {

    private static final Map<TaskState, Map<TaskEvent, TaskState>> MERGED = build();

    private TaskTransition() {
    }

    private static Map<TaskState, Map<TaskEvent, TaskState>> build() {
        var result = new java.util.EnumMap<TaskState, java.util.Map<TaskEvent, TaskState>>(TaskState.class);

        add(result, TaskState.CREATED, TaskEvent.BUG_SUBMITTED, TaskState.BUG_ANALYZING);
        add(result, TaskState.BUG_ANALYZING, TaskEvent.BUG_ANALYZED, TaskState.PROJECT_MATCHING);
        add(result, TaskState.BUG_ANALYZING, TaskEvent.NEED_HUMAN, TaskState.WAITING_CONFIRM);
        add(result, TaskState.PROJECT_MATCHING, TaskEvent.PROJECT_MATCHED, TaskState.PLANNING);
        add(result, TaskState.PROJECT_MATCHING, TaskEvent.NEED_HUMAN, TaskState.WAITING_PROJECT_CONFIRM);
        add(result, TaskState.WAITING_PROJECT_CONFIRM, TaskEvent.PROJECT_CONFIRMED, TaskState.PLANNING);
        add(result, TaskState.PLANNING, TaskEvent.PLAN_CREATED, TaskState.WAITING_CONFIRM);
        add(result, TaskState.WAITING_CONFIRM, TaskEvent.PLAN_CONFIRMED, TaskState.READY);
        add(result, TaskState.READY, TaskEvent.WORKSPACE_REQUESTED, TaskState.PREPARING_WORKSPACE);
        add(result, TaskState.PREPARING_WORKSPACE, TaskEvent.WORKSPACE_READY, TaskState.DEVELOPING);
        add(result, TaskState.DEVELOPING, TaskEvent.CODE_COMMITTED, TaskState.TESTING);
        add(result, TaskState.DEVELOPING, TaskEvent.NEED_HUMAN, TaskState.FAILED_NEEDS_HUMAN);
        add(result, TaskState.TESTING, TaskEvent.TEST_PASSED, TaskState.REVIEWING);
        add(result, TaskState.TESTING, TaskEvent.TEST_FAILED, TaskState.DEVELOPING);
        add(result, TaskState.TESTING, TaskEvent.NEED_HUMAN, TaskState.FAILED_NEEDS_HUMAN);
        add(result, TaskState.REVIEWING, TaskEvent.REVIEW_PASSED, TaskState.WAITING_APPROVAL);
        add(result, TaskState.REVIEWING, TaskEvent.REVIEW_FAILED, TaskState.DEVELOPING);
        add(result, TaskState.REVIEWING, TaskEvent.NEED_HUMAN, TaskState.FAILED_NEEDS_HUMAN);
        add(result, TaskState.WAITING_APPROVAL, TaskEvent.DELIVERY_APPROVED, TaskState.GIT_DELIVERING);
        add(result, TaskState.GIT_DELIVERING, TaskEvent.GIT_PUSHED, TaskState.BUILDING);
        add(result, TaskState.GIT_DELIVERING, TaskEvent.MR_CREATED, TaskState.BUILDING);
        add(result, TaskState.BUILDING, TaskEvent.CI_SUCCEEDED, TaskState.DEPLOYING_TEST);
        add(result, TaskState.BUILDING, TaskEvent.CI_FAILED, TaskState.DEVELOPING);
        add(result, TaskState.DEPLOYING_TEST, TaskEvent.TEST_ENV_DEPLOYED, TaskState.ACCEPTANCE);
        add(result, TaskState.ACCEPTANCE, TaskEvent.ACCEPTED, TaskState.WAITING_PROD_APPROVAL);
        add(result, TaskState.WAITING_PROD_APPROVAL, TaskEvent.PROD_APPROVED, TaskState.COMPLETED);
        add(result, TaskState.ACCEPTANCE, TaskEvent.COMPLETED, TaskState.COMPLETED);

        var immutable = new java.util.EnumMap<TaskState, Map<TaskEvent, TaskState>>(TaskState.class);
        result.forEach((state, transitions) -> immutable.put(state, Map.copyOf(transitions)));
        return Map.copyOf(immutable);
    }

    private static void add(Map<TaskState, Map<TaskEvent, TaskState>> table,
                            TaskState from, TaskEvent event, TaskState to) {
        table.computeIfAbsent(from, ignored -> new java.util.HashMap<>()).put(event, to);
    }

    public static boolean isTerminal(TaskState state) {
        return state.isTerminal();
    }

    public static List<TaskEvent> allowedEvents(TaskState state) {
        var transitions = MERGED.get(state);
        return transitions == null ? List.of() : List.copyOf(transitions.keySet());
    }

    public static TaskState next(TaskState current, TaskEvent event) {
        if (event == TaskEvent.CANCEL && !current.isTerminal()) {
            return TaskState.CANCELLED;
        }
        var transitions = MERGED.get(current);
        if (transitions == null || !transitions.containsKey(event)) {
            throw new IllegalStateException(
                    "Illegal transition: " + current + " --" + event + "-->");
        }
        return transitions.get(event);
    }
}
