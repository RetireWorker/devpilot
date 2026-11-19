package com.devpilot.project.api;

import com.devpilot.common.domain.RepositoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectRequest(
        @NotBlank @Size(max = 128) String id,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        List<String> aliases,
        List<String> serviceNames,
        List<String> logPackages,
        List<String> codePathKeywords,
        String team,
        String owner,
        String language,
        String javaVersion,
        String framework,
        RepositoryType repositoryType,
        String repositoryUrl,
        String defaultBranch,
        String svnUrl,
        String svnLayout,
        String targetRef,
        String workspaceStrategy,
        String workspaceRoot,
        String buildCommandKey,
        String testCommandKey,
        Boolean createMergeRequest,
        Boolean autoPush,
        Boolean requireReview,
        Boolean requireApproval) {
}
