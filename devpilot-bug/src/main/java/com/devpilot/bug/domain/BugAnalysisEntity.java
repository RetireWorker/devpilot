package com.devpilot.bug.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_bug_analysis")
public class BugAnalysisEntity {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "bug_id", length = 80, nullable = false)
    private String bugId;

    @Column(name = "problem_type", length = 60)
    private String problemType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "impact_scope", columnDefinition = "text")
    private String impactScope;

    @Column(name = "error_log_summary", columnDefinition = "text")
    private String errorLogSummary;

    @Column(name = "suspected_modules", columnDefinition = "text")
    private String suspectedModules;

    @Column(name = "reproducibility", length = 30)
    private String reproducibility;

    @Column(name = "missing_info", columnDefinition = "text")
    private String missingInfo;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

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

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public void setImpactScope(String impactScope) {
        this.impactScope = impactScope;
    }

    public String getErrorLogSummary() {
        return errorLogSummary;
    }

    public void setErrorLogSummary(String errorLogSummary) {
        this.errorLogSummary = errorLogSummary;
    }

    public String getSuspectedModules() {
        return suspectedModules;
    }

    public void setSuspectedModules(String suspectedModules) {
        this.suspectedModules = suspectedModules;
    }

    public String getReproducibility() {
        return reproducibility;
    }

    public void setReproducibility(String reproducibility) {
        this.reproducibility = reproducibility;
    }

    public String getMissingInfo() {
        return missingInfo;
    }

    public void setMissingInfo(String missingInfo) {
        this.missingInfo = missingInfo;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
