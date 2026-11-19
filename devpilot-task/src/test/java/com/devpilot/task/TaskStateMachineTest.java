package com.devpilot.task;

import com.devpilot.common.domain.TaskEvent;
import com.devpilot.common.domain.TaskState;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.repository.ExecutionEventRepository;
import com.devpilot.task.repository.TaskRepository;
import com.devpilot.task.service.TaskStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestJpaConfig.class, TaskStateMachine.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:task-sm;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class TaskStateMachineTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private TaskStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private void seed(String id, TaskState state) {
        TaskEntity task = TaskEntity.create(id, "BUG-1", "p1", "标题", "目标", 0.9, "{}");
        task.setState(state);
        taskRepository.saveAndFlush(task);
    }

    @Test
    void followsHappyPath() {
        seed("T1", TaskState.CREATED);

        assertThat(stateMachine.transition("T1", TaskEvent.BUG_SUBMITTED, "u", null, null))
                .isEqualTo(TaskState.BUG_ANALYZING);
        assertThat(stateMachine.transition("T1", TaskEvent.BUG_ANALYZED, "u", null, null))
                .isEqualTo(TaskState.PROJECT_MATCHING);
        assertThat(stateMachine.transition("T1", TaskEvent.PROJECT_MATCHED, "u", null, null))
                .isEqualTo(TaskState.PLANNING);
        assertThat(stateMachine.transition("T1", TaskEvent.PLAN_CREATED, "u", null, null))
                .isEqualTo(TaskState.WAITING_CONFIRM);
        assertThat(stateMachine.transition("T1", TaskEvent.PLAN_CONFIRMED, "u", null, null))
                .isEqualTo(TaskState.READY);
        assertThat(stateMachine.transition("T1", TaskEvent.WORKSPACE_REQUESTED, "u", null, null))
                .isEqualTo(TaskState.PREPARING_WORKSPACE);
        assertThat(stateMachine.transition("T1", TaskEvent.WORKSPACE_READY, "u", null, null))
                .isEqualTo(TaskState.DEVELOPING);
        assertThat(stateMachine.transition("T1", TaskEvent.CODE_COMMITTED, "u", null, null))
                .isEqualTo(TaskState.TESTING);
        assertThat(stateMachine.transition("T1", TaskEvent.TEST_PASSED, "u", null, null))
                .isEqualTo(TaskState.REVIEWING);
        assertThat(stateMachine.transition("T1", TaskEvent.REVIEW_PASSED, "u", null, null))
                .isEqualTo(TaskState.WAITING_APPROVAL);

        assertThat(eventRepository.findByTaskIdOrderByIdAsc("T1")).hasSize(10);
    }

    @Test
    void rejectsIllegalTransition() {
        seed("T2", TaskState.CREATED);

        assertThatThrownBy(() -> stateMachine.transition("T2", TaskEvent.TEST_PASSED, "u", null, null))
                .isInstanceOf(ConflictException.class);
        assertThat(taskRepository.findById("T2").orElseThrow().getState()).isEqualTo(TaskState.CREATED);
        assertThat(eventRepository.findByTaskIdOrderByIdAsc("T2")).isEmpty();
    }

    @Test
    void idempotentReplayDoesNotDuplicateEvent() {
        seed("T3", TaskState.CREATED);

        stateMachine.transition("T3", TaskEvent.BUG_SUBMITTED, "u", null, "key-1");
        TaskState replayed = stateMachine.transition("T3", TaskEvent.BUG_SUBMITTED, "u", null, "key-1");

        assertThat(replayed).isEqualTo(TaskState.BUG_ANALYZING);
        assertThat(eventRepository.findByTaskIdOrderByIdAsc("T3")).hasSize(1);
    }

    @Test
    void cancelWorksFromNonTerminalState() {
        seed("T4", TaskState.DEVELOPING);

        assertThat(stateMachine.transition("T4", TaskEvent.CANCEL, "u", "取消", null))
                .isEqualTo(TaskState.CANCELLED);
    }

    @Test
    void cancelFromTerminalIsRejected() {
        seed("T5", TaskState.COMPLETED);

        assertThatThrownBy(() -> stateMachine.transition("T5", TaskEvent.CANCEL, "u", "取消", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void optimisticVersionIncrements() {
        seed("T6", TaskState.CREATED);
        long before = taskRepository.findById("T6").orElseThrow().getVersion();

        stateMachine.transition("T6", TaskEvent.BUG_SUBMITTED, "u", null, null);

        assertThat(taskRepository.findById("T6").orElseThrow().getVersion()).isGreaterThan(before);
    }

    @Test
    void unknownTaskIsNotFound() {
        assertThatThrownBy(() -> stateMachine.transition("missing", TaskEvent.BUG_SUBMITTED, "u", null, null))
                .isInstanceOf(NotFoundException.class);
    }
}
