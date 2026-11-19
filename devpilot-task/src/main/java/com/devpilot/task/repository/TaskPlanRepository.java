package com.devpilot.task.repository;

import com.devpilot.task.domain.TaskPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskPlanRepository extends JpaRepository<TaskPlanEntity, Long> {

    Optional<TaskPlanEntity> findByTaskId(String taskId);

    void deleteByTaskId(String taskId);
}
