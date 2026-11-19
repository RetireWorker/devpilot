package com.devpilot.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_test_result")
public class TestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "round_no", nullable = false)
    private int round;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "report_ref", length = 500)
    private String reportRef;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TestResultEntity() {
    }

    public static TestResultEntity create(String taskId, int round, boolean passed, Integer exitCode,
                                          String reportRef, String summary) {
        TestResultEntity entity = new TestResultEntity();
        entity.taskId = taskId;
        entity.round = round;
        entity.passed = passed;
        entity.exitCode = exitCode;
        entity.reportRef = reportRef;
        entity.summary = summary;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getRound() {
        return round;
    }

    public boolean isPassed() {
        return passed;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getReportRef() {
        return reportRef;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
