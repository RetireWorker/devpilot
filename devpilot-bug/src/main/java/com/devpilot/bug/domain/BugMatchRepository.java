package com.devpilot.bug.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BugMatchRepository extends JpaRepository<BugMatchEntity, String> {

    Optional<BugMatchEntity> findFirstByBugIdOrderByCreatedAtDesc(String bugId);
}
