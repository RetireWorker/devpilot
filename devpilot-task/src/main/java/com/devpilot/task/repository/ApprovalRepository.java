package com.devpilot.task.repository;

import com.devpilot.task.domain.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, Long> {

    List<ApprovalEntity> findByTaskIdOrderByIdAsc(String taskId);
}
