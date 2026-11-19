package com.devpilot.task.repository;

import com.devpilot.task.domain.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {

    Optional<WorkspaceEntity> findByTaskId(String taskId);
}
