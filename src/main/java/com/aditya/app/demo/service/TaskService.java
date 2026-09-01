package com.aditya.app.demo.service;

import com.aditya.app.common.NotFoundException;
import com.aditya.app.demo.domain.Task;
import com.aditya.app.demo.domain.TaskStatus;
import com.aditya.app.demo.dto.CreateTaskRequest;
import com.aditya.app.demo.dto.TaskResponse;
import com.aditya.app.demo.repo.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task saved = repository.save(new Task(request.title()));
        log.info("Created task id={}", saved.getId());
        return TaskResponse.from(saved);
    }

    public List<TaskResponse> findAll(TaskStatus status) {
        List<Task> tasks = (status == null) ? repository.findAll() : repository.findByStatus(status);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(getOrThrow(id));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus target) {
        Task task = getOrThrow(id);
        task.transitionTo(target);
        return TaskResponse.from(repository.save(task));
    }

    private Task getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
    }
}
