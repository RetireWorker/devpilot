package com.devpilot.bug.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_bug_project_match")
public class BugMatchEntity {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "bug_id", length = 80, nullable = false)
    private String bugId;

    @Column(name = "project_id", length = 120)
    private String projectId;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "signals", columnDefinition = "text")
    private String signals;

    @Column(name = "candidates_json", columnDefinition = "text")
    private String candidatesJson;

    @Column(name = "decision", length = 40, nullable = false)
    private String decision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSignals() {
        return signals;
    }

    public void setSignals(String signals) {
        this.signals = signals;
    }

    public String getCandidatesJson() {
        return candidatesJson;
    }

    public void setCandidatesJson(String candidatesJson) {
        this.candidatesJson = candidatesJson;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
