package com.devpilot.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_workspace")
public class WorkspaceEntity {

    @Id
    @Column(name = "id", length = 120)
    private String id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "project_id", length = 120, nullable = false)
    private String projectId;

    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "base_ref", length = 300)
    private String baseRef;

    @Column(name = "head_commit", length = 100)
    private String headCommit;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceEntity() {
    }

    public static WorkspaceEntity create(String id, String taskId, String projectId, String relativePath,
                                         String branch, String baseRef, String headCommit, String status) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.id = id;
        entity.taskId = taskId;
        entity.projectId = projectId;
        entity.relativePath = relativePath;
        entity.branch = branch;
        entity.baseRef = baseRef;
        entity.headCommit = headCommit;
        entity.status = status;
        entity.createdAt = Instant.now();
        return entity;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getBranch() {
        return branch;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public String getHeadCommit() {
        return headCommit;
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
