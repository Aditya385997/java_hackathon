package com.aditya.app.demo.repo;

import com.aditya.app.demo.domain.Task;
import com.aditya.app.demo.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatus(TaskStatus status);
}
