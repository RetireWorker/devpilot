package com.devpilot.task.repository;

import com.devpilot.task.domain.TaskSubtaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskSubtaskRepository extends JpaRepository<TaskSubtaskEntity, Long> {

    List<TaskSubtaskEntity> findByTaskIdOrderByIdAsc(String taskId);

    void deleteByTaskId(String taskId);
}
