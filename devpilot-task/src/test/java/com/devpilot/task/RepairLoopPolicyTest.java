package com.devpilot.task;

import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.policy.RepairLoopPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairLoopPolicyTest {

    private TaskEntity taskWithRounds(int maxRounds) {
        TaskEntity task = TaskEntity.create("T", "BUG", "P", "t", "o", 0.9, "{}");
        task.setMaxRepairRounds(maxRounds);
        return task;
    }

    @Test
    void allowsRepairWithinBudget() {
        TaskEntity task = taskWithRounds(2);
        assertThat(RepairLoopPolicy.canAutoRepair(task)).isTrue();
        assertThat(RepairLoopPolicy.registerFailedAttempt(task)).isFalse();
        assertThat(RepairLoopPolicy.registerFailedAttempt(task)).isFalse();
        assertThat(RepairLoopPolicy.canAutoRepair(task)).isFalse();
    }

    @Test
    void escalatesWhenBudgetExhausted() {
        TaskEntity task = taskWithRounds(1);
        assertThat(RepairLoopPolicy.registerFailedAttempt(task)).isFalse();
        assertThat(RepairLoopPolicy.registerFailedAttempt(task)).isTrue();
    }
}
