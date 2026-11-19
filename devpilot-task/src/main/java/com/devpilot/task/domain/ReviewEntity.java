package com.devpilot.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "dev_review")
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "round_no", nullable = false)
    private int round;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Convert(converter = StringListConverter.class)
    @Column(name = "blocking_items")
    private List<String> blocking;

    @Convert(converter = StringListConverter.class)
    @Column(name = "warning_items")
    private List<String> warnings;

    @Convert(converter = StringListConverter.class)
    @Column(name = "risk_items")
    private List<String> risks;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewEntity() {
    }

    public static ReviewEntity create(String taskId, int round, boolean passed, List<String> blocking,
                                      List<String> warnings, List<String> risks, String summary) {
        ReviewEntity entity = new ReviewEntity();
        entity.taskId = taskId;
        entity.round = round;
        entity.passed = passed;
        entity.blocking = blocking;
        entity.warnings = warnings;
        entity.risks = risks;
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

    public List<String> getBlocking() {
        return blocking == null ? List.of() : blocking;
    }

    public List<String> getWarnings() {
        return warnings == null ? List.of() : warnings;
    }

    public List<String> getRisks() {
        return risks == null ? List.of() : risks;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
