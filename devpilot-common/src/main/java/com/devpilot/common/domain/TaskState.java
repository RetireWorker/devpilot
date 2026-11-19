package com.devpilot.common.domain;

/**
 * Canonical task lifecycle. The center owns transitions; agents only report
 * execution results.
 */
public enum TaskState {
    CREATED,
    BUG_ANALYZING,
    PROJECT_MATCHING,
    WAITING_PROJECT_CONFIRM,
    ANALYZING,
    WAITING_CONFIRM,
    PLANNING,
    READY,
    PREPARING_WORKSPACE,
    DEVELOPING,
    TESTING,
    REVIEWING,
    FAILED_NEEDS_HUMAN,
    WAITING_APPROVAL,
    GIT_DELIVERING,
    SVN_DCOMMITTING,
    BUILDING,
    DEPLOYING_TEST,
    ACCEPTANCE,
    WAITING_PROD_APPROVAL,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
