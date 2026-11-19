package com.devpilot.task.repository;

import com.devpilot.task.domain.ExecutionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEventEntity, Long> {

    List<ExecutionEventEntity> findByTaskIdOrderByIdAsc(String taskId);

    Optional<ExecutionEventEntity> findFirstByTaskIdAndIdempotencyKeyOrderByIdDesc(
            String taskId, String idempotencyKey);
}
