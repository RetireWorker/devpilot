package com.devpilot.task.repository;

import com.devpilot.task.domain.CodeChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeChangeRepository extends JpaRepository<CodeChangeEntity, Long> {

    Optional<CodeChangeEntity> findFirstByTaskIdOrderByIdDesc(String taskId);
}
