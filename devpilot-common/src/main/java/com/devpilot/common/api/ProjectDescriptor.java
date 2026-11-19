package com.devpilot.common.api;

import com.devpilot.common.domain.RepositoryType;

import java.util.List;

/**
 * Read-only project registration data shared across modules. Fields used for
 * BUG-to-project matching are kept separate from execution fields so the
 * matcher never needs repository internals.
 */
public record ProjectDescriptor(
        String id,
        String name,
        String description,
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
        boolean createMergeRequest,
        boolean autoPush,
        boolean requireReview,
        boolean requireApproval,
        boolean active) {
}
