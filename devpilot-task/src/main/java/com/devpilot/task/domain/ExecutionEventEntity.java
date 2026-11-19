package com.devpilot.task.domain;

import com.devpilot.common.domain.TaskEvent;
import com.devpilot.common.domain.TaskState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_execution_event",
        indexes = @Index(name = "idx_execution_event_task", columnList = "task_id, created_at"))
public class ExecutionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", length = 40, nullable = false)
    private TaskEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 40, nullable = false)
    private TaskState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 40, nullable = false)
    private TaskState toState;

    @Column(name = "actor", length = 120)
    private String actor;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExecutionEventEntity() {
    }

    public static ExecutionEventEntity create(String taskId, TaskEvent event, TaskState fromState,
                                              TaskState toState, String actor, String detail,
                                              String idempotencyKey) {
        ExecutionEventEntity entity = new ExecutionEventEntity();
        entity.taskId = taskId;
        entity.event = event;
        entity.fromState = fromState;
        entity.toState = toState;
        entity.actor = actor;
        entity.detail = detail;
        entity.idempotencyKey = idempotencyKey;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskEvent getEvent() {
        return event;
    }

    public TaskState getFromState() {
        return fromState;
    }

    public TaskState getToState() {
        return toState;
    }

    public String getActor() {
        return actor;
    }

    public String getDetail() {
        return detail;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
