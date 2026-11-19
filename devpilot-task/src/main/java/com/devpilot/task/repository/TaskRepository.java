package com.devpilot.task.repository;

import com.devpilot.task.domain.TaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    Page<TaskEntity> findByProjectId(String projectId, Pageable pageable);

    Optional<TaskEntity> findByBugId(String bugId);
}
