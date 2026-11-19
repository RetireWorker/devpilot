package com.devpilot.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_approval")
public class ApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "approval_type", length = 40, nullable = false)
    private String type;

    @Column(name = "decision", length = 40, nullable = false)
    private String decision;

    @Column(name = "approver", length = 120)
    private String approver;

    @Column(name = "approval_comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalEntity() {
    }

    public static ApprovalEntity create(String taskId, String type, String decision,
                                        String approver, String comment) {
        ApprovalEntity entity = new ApprovalEntity();
        entity.taskId = taskId;
        entity.type = type;
        entity.decision = decision;
        entity.approver = approver;
        entity.comment = comment;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getType() {
        return type;
    }

    public String getDecision() {
        return decision;
    }

    public String getApprover() {
        return approver;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
