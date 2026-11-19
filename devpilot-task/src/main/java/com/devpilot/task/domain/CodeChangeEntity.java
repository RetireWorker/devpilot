package com.devpilot.task.domain;

import com.devpilot.common.api.CodeChangeView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dev_code_change")
public class CodeChangeEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 80, nullable = false)
    private String taskId;

    @Column(name = "workspace_id", length = 120)
    private String workspaceId;

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "commit_sha", length = 100)
    private String commitSha;

    @Column(name = "base_ref", length = 300)
    private String baseRef;

    @Column(name = "files_json", columnDefinition = "text")
    private String filesJson;

    @Column(name = "insertions")
    private int insertions;

    @Column(name = "deletions")
    private int deletions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CodeChangeEntity() {
    }

    public static CodeChangeEntity create(CodeChangeView view) {
        CodeChangeEntity entity = new CodeChangeEntity();
        entity.taskId = view.taskId();
        entity.workspaceId = view.workspaceId();
        entity.branch = view.branch();
        entity.commitSha = view.commitSha();
        entity.baseRef = view.baseRef();
        entity.insertions = view.insertions();
        entity.deletions = view.deletions();
        entity.filesJson = serialize(view);
        entity.createdAt = Instant.now();
        return entity;
    }

    private static String serialize(CodeChangeView view) {
        try {
            return MAPPER.writeValueAsString(view.files());
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化变更文件", e);
        }
    }

    public CodeChangeView toView() {
        try {
            var type = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<CodeChangeView.ChangedFile>>() {
            };
            var files = filesJson == null ? java.util.List.<CodeChangeView.ChangedFile>of()
                    : MAPPER.<java.util.List<CodeChangeView.ChangedFile>>readValue(filesJson, type);
            return new CodeChangeView(taskId, workspaceId, branch, commitSha, baseRef, files, insertions, deletions);
        } catch (Exception e) {
            throw new IllegalStateException("无法反序列化变更文件", e);
        }
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
