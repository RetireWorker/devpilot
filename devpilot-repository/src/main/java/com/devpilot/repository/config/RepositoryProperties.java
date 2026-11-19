package com.devpilot.repository.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "ai.repository")
public record RepositoryProperties(
        Path root,
        Path mirrorRoot,
        String gitBin,
        String svnBin,
        Duration commandTimeout) {

    public RepositoryProperties {
        root = root == null ? Path.of("data/workspaces") : root;
        mirrorRoot = mirrorRoot == null ? Path.of("data/mirrors") : mirrorRoot;
        gitBin = gitBin == null || gitBin.isBlank() ? "git" : gitBin;
        svnBin = svnBin == null || svnBin.isBlank() ? "svn" : svnBin;
        commandTimeout = commandTimeout == null ? Duration.ofMinutes(10) : commandTimeout;
    }
}
