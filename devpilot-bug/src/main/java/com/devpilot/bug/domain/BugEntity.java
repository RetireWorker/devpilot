package com.devpilot.bug.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_bug")
public class BugEntity {

    @Id
    @Column(name = "id", length = 80, nullable = false)
    private String id;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "reproduction_steps")
    private String reproductionSteps;

    @Column(name = "expected_result")
    private String expectedResult;

    @Column(name = "actual_result")
    private String actualResult;

    @Column(name = "environment", length = 500)
    private String environment;

    @Column(name = "version", length = 120)
    private String version;

    @Column(name = "time_range", length = 200)
    private String timeRange;

    @Column(name = "error_log")
    private String errorLog;

    @Column(name = "severity", length = 20, nullable = false)
    private String severity;

    @Column(name = "production_issue", nullable = false)
    private boolean productionIssue;

    @Column(name = "specified_project_id", length = 120)
    private String specifiedProjectId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BugEntity create(String id, String title, String description,
                                   String reproductionSteps, String expectedResult,
                                   String actualResult, String environment, String version,
                                   String timeRange, String errorLog, String severity,
                                   boolean productionIssue, String specifiedProjectId) {
        var entity = new BugEntity();
        entity.id = id;
        entity.title = title;
        entity.description = description;
        entity.reproductionSteps = reproductionSteps;
        entity.expectedResult = expectedResult;
        entity.actualResult = actualResult;
        entity.environment = environment;
        entity.version = version;
        entity.timeRange = timeRange;
        entity.errorLog = errorLog;
        entity.severity = severity;
        entity.productionIssue = productionIssue;
        entity.specifiedProjectId = specifiedProjectId;
        entity.status = "CREATED";
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void markStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getReproductionSteps() {
        return reproductionSteps;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getActualResult() {
        return actualResult;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getVersion() {
        return version;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public String getErrorLog() {
        return errorLog;
    }

    public String getSeverity() {
        return severity;
    }

    public boolean isProductionIssue() {
        return productionIssue;
    }

    public String getSpecifiedProjectId() {
        return specifiedProjectId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
