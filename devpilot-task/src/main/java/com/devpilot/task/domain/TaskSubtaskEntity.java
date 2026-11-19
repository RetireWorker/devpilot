package com.devpilot.task.domain;

import com.devpilot.common.api.AgentRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "dev_task_subtask")
public class TaskSubtaskEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "subtask_key", length = 80, nullable = false)
    private String subtaskKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", length = 40, nullable = false)
    private AgentRole role;

    @Column(name = "subtask_type", length = 40)
    private String type;

    @Convert(converter = StringListConverter.class)
    @Column(name = "depends_on")
    private List<String> dependsOn;

    @Convert(converter = StringListConverter.class)
    @Column(name = "inputs")
    private List<String> inputs;

    @Convert(converter = StringListConverter.class)
    @Column(name = "outputs")
    private List<String> outputs;

    @Convert(converter = StringListConverter.class)
    @Column(name = "allowed_paths")
    private List<String> allowedPaths;

    @Column(name = "acceptance", columnDefinition = "text")
    private String acceptance;

    @Column(name = "status", length = 30, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskSubtaskEntity() {
    }

    public static TaskSubtaskEntity create(String taskId, String key, AgentRole role, String type,
                                           List<String> dependsOn, List<String> inputs, List<String> outputs,
                                           List<String> allowedPaths, String acceptance) {
        TaskSubtaskEntity entity = new TaskSubtaskEntity();
        entity.taskId = taskId;
        entity.subtaskKey = key;
        entity.role = role;
        entity.type = type;
        entity.dependsOn = dependsOn;
        entity.inputs = inputs;
        entity.outputs = outputs;
        entity.allowedPaths = allowedPaths;
        entity.acceptance = acceptance;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSubtaskKey() {
        return subtaskKey;
    }

    public AgentRole getRole() {
        return role;
    }

    public String getType() {
        return type;
    }

    public List<String> getDependsOn() {
        return dependsOn == null ? List.of() : dependsOn;
    }

    public List<String> getInputs() {
        return inputs == null ? List.of() : inputs;
    }

    public List<String> getOutputs() {
        return outputs == null ? List.of() : outputs;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths == null ? List.of() : allowedPaths;
    }

    public String getAcceptance() {
        return acceptance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
