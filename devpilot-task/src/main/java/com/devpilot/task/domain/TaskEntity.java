package com.devpilot.task.domain;

import com.devpilot.common.domain.TaskState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "dev_task")
public class TaskEntity {

    @Id
    @Column(name = "id", length = 80)
    private String id;

    @Column(name = "bug_id", length = 80)
    private String bugId;

    @Column(name = "project_id", length = 120)
    private String projectId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "objective", columnDefinition = "text")
    private String objective;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 40, nullable = false)
    private TaskState state = TaskState.CREATED;

    @Column(name = "repair_round", nullable = false)
    private int repairRound;

    @Column(name = "max_repair_rounds", nullable = false)
    private int maxRepairRounds = 2;

    @Column(name = "matched_confidence")
    private double matchedConfidence;

    @Column(name = "workspace_id", length = 120)
    private String workspaceId;

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "bug_analysis_json", columnDefinition = "text")
    private String bugAnalysisJson;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskEntity() {
    }

    public static TaskEntity create(String id, String bugId, String projectId, String title,
                                    String objective, double confidence, String bugAnalysisJson) {
        TaskEntity entity = new TaskEntity();
        entity.id = id;
        entity.bugId = bugId;
        entity.projectId = projectId;
        entity.title = title;
        entity.objective = objective;
        entity.matchedConfidence = confidence;
        entity.bugAnalysisJson = bugAnalysisJson;
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public String getId() {
        return id;
    }

    public String getBugId() {
        return bugId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    public int getRepairRound() {
        return repairRound;
    }

    public void incrementRepairRound() {
        this.repairRound++;
    }

    public int getMaxRepairRounds() {
        return maxRepairRounds;
    }

    public void setMaxRepairRounds(int maxRepairRounds) {
        this.maxRepairRounds = maxRepairRounds;
    }

    public double getMatchedConfidence() {
        return matchedConfidence;
    }

    public void setMatchedConfidence(double matchedConfidence) {
        this.matchedConfidence = matchedConfidence;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getBugAnalysisJson() {
        return bugAnalysisJson;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
