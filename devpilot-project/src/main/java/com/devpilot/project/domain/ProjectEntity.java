package com.devpilot.project.domain;

import com.devpilot.common.domain.RepositoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dev_project")
@Getter
@Setter
public class ProjectEntity {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "aliases", columnDefinition = "text")
    private List<String> aliases = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "service_names", columnDefinition = "text")
    private List<String> serviceNames = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "log_packages", columnDefinition = "text")
    private List<String> logPackages = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "code_path_keywords", columnDefinition = "text")
    private List<String> codePathKeywords = new ArrayList<>();

    @Column(name = "team", length = 128)
    private String team;

    @Column(name = "owner", length = 128)
    private String owner;

    @Column(name = "language", length = 64)
    private String language;

    @Column(name = "java_version", length = 32)
    private String javaVersion;

    @Column(name = "framework", length = 128)
    private String framework;

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_type", nullable = false, length = 32)
    private RepositoryType repositoryType = RepositoryType.GIT;

    @Column(name = "repository_url", length = 1000)
    private String repositoryUrl;

    @Column(name = "default_branch", length = 256)
    private String defaultBranch;

    @Column(name = "svn_url", length = 1000)
    private String svnUrl;

    @Column(name = "svn_layout", length = 64)
    private String svnLayout;

    @Column(name = "target_ref", length = 256)
    private String targetRef;

    @Column(name = "workspace_strategy", length = 64)
    private String workspaceStrategy;

    @Column(name = "workspace_root", length = 512)
    private String workspaceRoot;

    @Column(name = "build_command_key", length = 128)
    private String buildCommandKey;

    @Column(name = "test_command_key", length = 128)
    private String testCommandKey;

    @Column(name = "create_merge_request", nullable = false)
    private boolean createMergeRequest;

    @Column(name = "auto_push", nullable = false)
    private boolean autoPush;

    @Column(name = "require_review", nullable = false)
    private boolean requireReview = true;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
