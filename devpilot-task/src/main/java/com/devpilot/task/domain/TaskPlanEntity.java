package com.devpilot.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_task_plan")
public class TaskPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false, unique = true)
    private String taskId;

    @Column(name = "objective", columnDefinition = "text")
    private String objective;

    @Column(name = "requires_human_confirmation", nullable = false)
    private boolean requiresHumanConfirmation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskPlanEntity() {
    }

    public static TaskPlanEntity create(String taskId, String objective, boolean requiresHumanConfirmation) {
        TaskPlanEntity entity = new TaskPlanEntity();
        entity.taskId = taskId;
        entity.objective = objective;
        entity.requiresHumanConfirmation = requiresHumanConfirmation;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getObjective() {
        return objective;
    }

    public boolean isRequiresHumanConfirmation() {
        return requiresHumanConfirmation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
