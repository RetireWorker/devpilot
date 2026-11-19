package com.devpilot.task.policy;

import com.devpilot.task.domain.TaskEntity;

/**
 * Bounds automatic repair. Exceeding the budget must stop automation rather
 * than loop forever or let an agent weaken tests to pass.
 */
public final class RepairLoopPolicy {

    private RepairLoopPolicy() {
    }

    public static boolean canAutoRepair(TaskEntity task) {
        return task.getRepairRound() < task.getMaxRepairRounds();
    }

    /**
     * @return true when the caller should escalate to FAILED_NEEDS_HUMAN.
     */
    public static boolean registerFailedAttempt(TaskEntity task) {
        task.incrementRepairRound();
        return task.getRepairRound() > task.getMaxRepairRounds();
    }
}
