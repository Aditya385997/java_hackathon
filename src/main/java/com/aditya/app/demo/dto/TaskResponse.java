package com.aditya.app.demo.dto;

import com.aditya.app.demo.domain.Task;
import com.aditya.app.demo.domain.TaskStatus;

import java.time.Instant;

public record TaskResponse(Long id, String title, TaskStatus status, Instant createdAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getStatus(), task.getCreatedAt());
    }
}
