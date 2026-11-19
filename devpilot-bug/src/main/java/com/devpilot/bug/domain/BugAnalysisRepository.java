package com.devpilot.bug.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BugAnalysisRepository extends JpaRepository<BugAnalysisEntity, String> {

    Optional<BugAnalysisEntity> findFirstByBugIdOrderByCreatedAtDesc(String bugId);
}
