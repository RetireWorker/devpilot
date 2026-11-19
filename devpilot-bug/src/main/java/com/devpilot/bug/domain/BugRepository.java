package com.devpilot.bug.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

public interface BugRepository extends JpaRepository<BugEntity, String> {

    Optional<BugEntity> findFirstByTitleAndStatusNotAndCreatedAtAfter(
            String title, String status, Instant after);

    Page<BugEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
