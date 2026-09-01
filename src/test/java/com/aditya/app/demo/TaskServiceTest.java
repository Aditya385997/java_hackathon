package com.aditya.app.demo;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.common.NotFoundException;
import com.aditya.app.demo.domain.Task;
import com.aditya.app.demo.domain.TaskStatus;
import com.aditya.app.demo.dto.CreateTaskRequest;
import com.aditya.app.demo.dto.TaskResponse;
import com.aditya.app.demo.repo.TaskRepository;
import com.aditya.app.demo.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void createsTaskInNewStatus() {
        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = service.create(new CreateTaskRequest("Write tests"));

        assertThat(response.title()).isEqualTo("Write tests");
        assertThat(response.status()).isEqualTo(TaskStatus.NEW);
    }

    @Test
    void throwsNotFoundWhenTaskMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsIllegalStatusTransition() {
        Task task = new Task("Already done");
        task.transitionTo(TaskStatus.IN_PROGRESS);
        task.transitionTo(TaskStatus.DONE);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateStatus(1L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(BusinessRuleException.class);
    }
}
