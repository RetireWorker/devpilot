package com.devpilot.task.service;

import com.devpilot.common.domain.TaskEvent;
import com.devpilot.common.domain.TaskState;
import com.devpilot.common.domain.TaskTransition;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.task.domain.ExecutionEventEntity;
import com.devpilot.task.domain.TaskEntity;
import com.devpilot.task.repository.ExecutionEventRepository;
import com.devpilot.task.repository.TaskRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single write path for task state. Callers pass an idempotency key for
 * externally triggered events; a replay returns the current state instead of
 * appending a duplicate event.
 */
@Service
public class TaskStateMachine {

    private final TaskRepository taskRepository;
    private final ExecutionEventRepository eventRepository;

    public TaskStateMachine(TaskRepository taskRepository, ExecutionEventRepository eventRepository) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public TaskState transition(String taskId, TaskEvent event, String actor,
                                String detail, String idempotencyKey) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在: " + taskId));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = eventRepository
                    .findFirstByTaskIdAndIdempotencyKeyOrderByIdDesc(taskId, idempotencyKey);
            if (existing.isPresent()) {
                return task.getState();
            }
        }

        TaskState from = task.getState();
        if (from.isTerminal()) {
            throw new ConflictException("任务已结束，拒绝状态变更: " + taskId + " [" + from + "]");
        }

        TaskState to;
        try {
            to = TaskTransition.next(from, event);
        } catch (IllegalStateException e) {
            throw new ConflictException("非法状态迁移: " + from + " --" + event + "-->");
        }

        task.setState(to);
        task.touch();
        try {
            taskRepository.saveAndFlush(task);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("任务状态已被并发修改，请重试: " + taskId);
        }

        eventRepository.save(ExecutionEventEntity.create(
                taskId, event, from, to, actor, detail, idempotencyKey));
        return to;
    }
}
