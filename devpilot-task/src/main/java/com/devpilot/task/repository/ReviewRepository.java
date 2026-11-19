package com.devpilot.task.repository;

import com.devpilot.task.domain.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    List<ReviewEntity> findByTaskIdOrderByIdAsc(String taskId);
}
