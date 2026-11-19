package com.devpilot.task.repository;

import com.devpilot.task.domain.TestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResultEntity, Long> {

    List<TestResultEntity> findByTaskIdOrderByIdAsc(String taskId);

    long countByTaskIdAndPassedTrue(String taskId);
}
